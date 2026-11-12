
package com.aisimulatorhub.backend.lti.service;

import com.aisimulatorhub.backend.lti.security.LtiSecurityUtils;
import com.aisimulatorhub.backend.model.PlatformRegistration;
import com.aisimulatorhub.backend.repository.LtiJwtReplayRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LtiJwtReplayServiceTests {

    @Autowired
    private LtiJwtReplayService replayService;

    @Autowired
    private LtiJwtReplayRepository replayRepository;

    private PlatformRegistration registration(UUID id) {
        PlatformRegistration registration =
                new PlatformRegistration();

        registration.setId(id);

        return registration;
    }

    @Test
    void firstUseOfJtiShouldBeAccepted() {

        PlatformRegistration registration =
                registration(UUID.randomUUID());

        assertDoesNotThrow(() ->
                replayService.registerToken(
                        registration,
                        "test-jti-" + UUID.randomUUID(),
                        Instant.now().plusSeconds(300)
                )
        );
    }

    @Test
    void repeatedJtiForSameRegistrationShouldBeRejected() {

        PlatformRegistration registration =
                registration(UUID.randomUUID());

        String jti = "duplicate-jti-" + UUID.randomUUID();

        replayService.registerToken(
                registration,
                jti,
                Instant.now().plusSeconds(300)
        );

        assertThrows(
                ResponseStatusException.class,
                () -> replayService.registerToken(
                        registration,
                        jti,
                        Instant.now().plusSeconds(300)
                )
        );
    }

    @Test
    void sameJtiForDifferentRegistrationsShouldBeAccepted() {

        String jti = "shared-jti-" + UUID.randomUUID();

        PlatformRegistration firstRegistration =
                registration(UUID.randomUUID());

        PlatformRegistration secondRegistration =
                registration(UUID.randomUUID());

        assertDoesNotThrow(() ->
                replayService.registerToken(
                        firstRegistration,
                        jti,
                        Instant.now().plusSeconds(300)
                )
        );

        assertDoesNotThrow(() ->
                replayService.registerToken(
                        secondRegistration,
                        jti,
                        Instant.now().plusSeconds(300)
                )
        );
    }

    @Test
    void expiredTokenShouldBeRejected() {

        PlatformRegistration registration =
                registration(UUID.randomUUID());

        assertThrows(
                ResponseStatusException.class,
                () -> replayService.registerToken(
                        registration,
                        "expired-jti-" + UUID.randomUUID(),
                        Instant.now().minusSeconds(60)
                )
        );
    }

    @Test
    void concurrentRequestsWithSameJtiShouldOnlyAllowOne() throws Exception {

        PlatformRegistration registration =
                registration(UUID.randomUUID());

        String jti = "concurrent-jti-" + UUID.randomUUID();

        Instant expiration = Instant.now().plusSeconds(300);

        int numberOfRequests = 2;

        ExecutorService executor =
                Executors.newFixedThreadPool(numberOfRequests);

        CountDownLatch ready =
                new CountDownLatch(numberOfRequests);

        CountDownLatch start =
                new CountDownLatch(1);

        try {

            Future<Boolean> first = executor.submit(() -> {
                ready.countDown();
                start.await();

                try {
                    replayService.registerToken(
                            registration,
                            jti,
                            expiration
                    );

                    return true;

                } catch (ResponseStatusException exception) {
                    return false;
                }
            });

            Future<Boolean> second = executor.submit(() -> {
                ready.countDown();
                start.await();

                try {
                    replayService.registerToken(
                            registration,
                            jti,
                            expiration
                    );

                    return true;

                } catch (ResponseStatusException exception) {
                    return false;
                }
            });

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Concurrent test threads did not become ready"
            );

            start.countDown();

            int accepted = 0;

            if (first.get(15, TimeUnit.SECONDS)) {
                accepted++;
            }

            if (second.get(15, TimeUnit.SECONDS)) {
                accepted++;
            }

            assertEquals(
                    1,
                    accepted,
                    "Exactly one concurrent request should be accepted"
            );

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void replayIdentifierShouldBeStoredAsSha256Hash() {

        String jti = "hash-test-" + UUID.randomUUID();

        String hash = LtiSecurityUtils.sha256(jti);

        assertEquals(64, hash.length());
        assertNotEquals(jti, hash);
    }
}
