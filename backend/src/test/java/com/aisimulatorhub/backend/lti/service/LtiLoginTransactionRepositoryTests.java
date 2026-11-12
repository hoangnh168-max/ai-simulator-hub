
package com.aisimulatorhub.backend.lti.service;

import com.aisimulatorhub.backend.lti.security.LtiSecurityUtils;
import com.aisimulatorhub.backend.model.LtiLoginTransaction;
import com.aisimulatorhub.backend.repository.LtiLoginTransactionRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LtiLoginTransactionRepositoryTests {

    @Autowired
    private LtiLoginTransactionRepository transactionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private LtiLoginTransaction createTransaction(
            Instant expiresAt
    ) {
        LtiLoginTransaction transaction =
                new LtiLoginTransaction();

        transaction.setPlatformIssuer(
                "https://platform-test.example"
        );

        transaction.setClientId("test-client");

        transaction.setStateHash(
                LtiSecurityUtils.sha256(
                        UUID.randomUUID().toString()
                )
        );

        transaction.setNonceHash(
                LtiSecurityUtils.sha256(
                        UUID.randomUUID().toString()
                )
        );

        transaction.setTargetLinkUri(
                "http://localhost:8080/lti/launch"
        );

        transaction.setExpiresAt(expiresAt);

        return transactionRepository.saveAndFlush(transaction);
    }

    @Test
    void validTransactionShouldBeConsumedExactlyOnce() {

        LtiLoginTransaction transaction =
                createTransaction(Instant.now().plusSeconds(300));

        try {
            Instant now = Instant.now();

            int firstResult =
                    transactionRepository.consumeIfValid(
                            transaction.getId(),
                            now
                    );

            int secondResult =
                    transactionRepository.consumeIfValid(
                            transaction.getId(),
                            now.plusMillis(1)
                    );

            assertEquals(1, firstResult);
            assertEquals(0, secondResult);

        } finally {
            transactionRepository.deleteById(transaction.getId());
        }
    }

    @Test
    void expiredTransactionShouldNotBeConsumed() {

        LtiLoginTransaction transaction =
                createTransaction(Instant.now().minusSeconds(60));

        try {
            int result =
                    transactionRepository.consumeIfValid(
                            transaction.getId(),
                            Instant.now()
                    );

            assertEquals(0, result);

        } finally {
            transactionRepository.deleteById(transaction.getId());
        }
    }

    @Test
    void unknownTransactionShouldNotBeConsumed() {

        int result =
                transactionRepository.consumeIfValid(
                        UUID.randomUUID(),
                        Instant.now()
                );

        assertEquals(0, result);
    }

    @Test
    void concurrentRequestsShouldConsumeTransactionExactlyOnce()
            throws Exception {

        LtiLoginTransaction transaction =
                createTransaction(Instant.now().plusSeconds(300));

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CyclicBarrier barrier = new CyclicBarrier(2);

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        try {
            Future<Integer> firstRequest = executor.submit(() -> {

                barrier.await(10, TimeUnit.SECONDS);

                return transactionTemplate.execute(status ->
                        transactionRepository.consumeIfValid(
                                transaction.getId(),
                                Instant.now()
                        )
                );
            });

            Future<Integer> secondRequest = executor.submit(() -> {

                barrier.await(10, TimeUnit.SECONDS);

                return transactionTemplate.execute(status ->
                        transactionRepository.consumeIfValid(
                                transaction.getId(),
                                Instant.now()
                        )
                );
            });

            Integer firstResult =
                    firstRequest.get(20, TimeUnit.SECONDS);

            Integer secondResult =
                    secondRequest.get(20, TimeUnit.SECONDS);

            assertNotNull(firstResult);
            assertNotNull(secondResult);

            assertTrue(
                    (firstResult == 1 && secondResult == 0)
                    || (firstResult == 0 && secondResult == 1),
                    "Exactly one concurrent request must consume the transaction"
            );

            assertEquals(
                    1,
                    firstResult + secondResult,
                    "Only one request may successfully consume the login state"
            );

            LtiLoginTransaction storedTransaction =
                    transactionRepository.findById(transaction.getId())
                            .orElseThrow();

            assertNotNull(
                    storedTransaction.getConsumedAt(),
                    "The transaction must be marked as consumed"
            );

        } finally {
            executor.shutdownNow();

            transactionRepository.deleteById(transaction.getId());
        }
    }
}
