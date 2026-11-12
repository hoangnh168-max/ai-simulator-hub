
package com.aisimulatorhub.backend.lti.service;

import com.aisimulatorhub.backend.lti.dto.ValidatedLtiLaunch;
import com.aisimulatorhub.backend.lti.security.LtiSecurityUtils;
import com.aisimulatorhub.backend.model.LtiLoginTransaction;
import com.aisimulatorhub.backend.model.PlatformRegistration;
import com.aisimulatorhub.backend.repository.LtiLoginTransactionRepository;
import com.aisimulatorhub.backend.repository.LtiJwtReplayRepository;
import com.aisimulatorhub.backend.repository.PlatformRegistrationRepository;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "lti.platform.base-url=http://localhost:8080"
})
class LtiLaunchTransactionTests {

    private static final String ISSUER =
            "https://platform-test.example";

    private static final String CLIENT_ID =
            "test-client";

    private static final String DEPLOYMENT_ID =
            "test-deployment";

    private static final String DEPLOYMENT_ID_CLAIM =
            "https://purl.imsglobal.org/spec/lti/claim/deployment_id";

    @Autowired
    private LtiLaunchService launchService;

    @Autowired
    private LtiLoginTransactionRepository transactionRepository;

    @Autowired
    private PlatformRegistrationRepository platformRepository;

    @Autowired
    private LtiJwtReplayRepository replayRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private LtiTokenValidationService tokenValidationService;

    @MockitoBean
    private LtiClaimValidationService claimValidationService;

    @Test
    void replayRecordShouldRollbackWhenLoginTransactionConsumptionFails()
            throws Exception {

        String state = "test-state-" + UUID.randomUUID();

        String jti = "test-jti-" + UUID.randomUUID();

        PlatformRegistration registration =
                createRegistration();

        LtiLoginTransaction transaction =
                createLoginTransaction(state);

        String idToken = createSignedToken();

        String jtiHash = LtiSecurityUtils.sha256(jti);

        TransactionTemplate competingTransaction =
                new TransactionTemplate(transactionManager);

        competingTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );

        when(tokenValidationService.verifySignature(
                anyString(),
                any(PlatformRegistration.class)
        )).thenAnswer(invocation ->
                SignedJWT.parse(invocation.getArgument(0))
        );

        when(claimValidationService.validate(
                any(SignedJWT.class),
                any(PlatformRegistration.class),
                anyString()
        )).thenAnswer(invocation -> {

            // Simulate another request consuming the same state
            // and committing independently while this launch runs.
            Integer competingResult =
                    competingTransaction.execute(status ->
                            transactionRepository.consumeIfValid(
                                    transaction.getId(),
                                    Instant.now()
                            )
                    );

            assertNotNull(competingResult);
            assertEquals(1, competingResult);

            return new ValidatedLtiLaunch(
                    ISSUER,
                    CLIENT_ID,
                    DEPLOYMENT_ID,
                    "LtiResourceLinkRequest",
                    "1.3.0",
                    "test-nonce",
                    jti,
                    Instant.now().plusSeconds(300),
                    "test-resource",
                    "test-subject",
                    List.of(CLIENT_ID),
                    Map.of()
            );
        });

        try {

            // The competing transaction consumes the login state.
            // The launch then attempts to consume it again.
            assertThrows(
                    ResponseStatusException.class,
                    () -> launchService.processLaunch(idToken, state)
            );

            // The competing transaction's consumption must remain committed.
            LtiLoginTransaction storedTransaction =
                    transactionRepository.findById(transaction.getId())
                            .orElseThrow();

            assertNotNull(
                    storedTransaction.getConsumedAt(),
                    "The competing transaction should have consumed the state"
            );

            // The replay insert performed by the failed launch must roll back.
            Integer replayCount = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM lti_jwt_replays
                    WHERE registration_id = ?
                      AND jti_hash = ?
                    """,
                    Integer.class,
                    registration.getId(),
                    jtiHash
            );

            assertEquals(
                    0,
                    replayCount,
                    "JWT replay record must roll back with the failed launch"
            );

        } finally {

            jdbcTemplate.update(
                    "DELETE FROM lti_jwt_replays WHERE registration_id = ?",
                    registration.getId()
            );

            transactionRepository.deleteById(transaction.getId());

            platformRepository.deleteById(registration.getId());
        }
    }

    @Test
    void duplicateJtiAcrossDifferentLoginTransactionsShouldBeRejected()
            throws Exception {

        String firstState = "first-state-" + UUID.randomUUID();
        String secondState = "second-state-" + UUID.randomUUID();

        String jti = "shared-jti-" + UUID.randomUUID();
        String jtiHash = LtiSecurityUtils.sha256(jti);

        PlatformRegistration registration =
                createRegistration();

        LtiLoginTransaction firstTransaction =
                createLoginTransaction(firstState);

        LtiLoginTransaction secondTransaction =
                createLoginTransaction(secondState);

        String idToken = createSignedToken();

        when(tokenValidationService.verifySignature(
                anyString(),
                any(PlatformRegistration.class)
        )).thenAnswer(invocation ->
                SignedJWT.parse(invocation.getArgument(0))
        );

        when(claimValidationService.validate(
                any(SignedJWT.class),
                any(PlatformRegistration.class),
                anyString()
        )).thenReturn(
                new ValidatedLtiLaunch(
                        ISSUER,
                        CLIENT_ID,
                        DEPLOYMENT_ID,
                        "LtiResourceLinkRequest",
                        "1.3.0",
                        "test-nonce",
                        jti,
                        Instant.now().plusSeconds(300),
                        "test-resource",
                        "test-subject",
                        List.of(CLIENT_ID),
                        Map.of()
                )
        );

        try {

            // First login transaction: the JWT should be accepted.
            ValidatedLtiLaunch firstLaunch =
                    launchService.processLaunch(idToken, firstState);

            assertNotNull(firstLaunch);

            // Second login transaction: the same JWT jti must be rejected.
            assertThrows(
                    ResponseStatusException.class,
                    () -> launchService.processLaunch(idToken, secondState),
                    "A JWT jti must not be accepted twice"
            );

            // The first login transaction must remain consumed.
            LtiLoginTransaction storedFirst =
                    transactionRepository.findById(firstTransaction.getId())
                            .orElseThrow();

            assertNotNull(
                    storedFirst.getConsumedAt(),
                    "The first login transaction should be consumed"
            );

            // The failed second launch must not consume its login transaction.
            LtiLoginTransaction storedSecond =
                    transactionRepository.findById(secondTransaction.getId())
                            .orElseThrow();

            assertNull(
                    storedSecond.getConsumedAt(),
                    "The second login transaction must remain unconsumed"
            );

            // Exactly one replay record should exist for this registration/jti.
            Integer replayCount = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM lti_jwt_replays
                    WHERE registration_id = ?
                      AND jti_hash = ?
                    """,
                    Integer.class,
                    registration.getId(),
                    jtiHash
            );

            assertEquals(
                    1,
                    replayCount,
                    "Only the first launch should register the JWT jti"
            );

        } finally {

            jdbcTemplate.update(
                    "DELETE FROM lti_jwt_replays WHERE registration_id = ?",
                    registration.getId()
            );

            transactionRepository.deleteById(firstTransaction.getId());
            transactionRepository.deleteById(secondTransaction.getId());

            platformRepository.deleteById(registration.getId());
        }
    }

    private PlatformRegistration createRegistration() {

        PlatformRegistration registration =
                new PlatformRegistration();

        registration.setPlatformName("LTI Transaction Test Platform");
        registration.setPlatformIssuer(ISSUER);
        registration.setClientId(CLIENT_ID);
        registration.setDeploymentId(DEPLOYMENT_ID);

        registration.setAuthorizationEndpoint(
                "https://platform-test.example/authorize"
        );

        registration.setTokenEndpoint(
                "https://platform-test.example/token"
        );

        registration.setPlatformJwksUrl(
                "https://platform-test.example/jwks"
        );

        registration.setEnabled(true);

        return platformRepository.saveAndFlush(registration);
    }

    private LtiLoginTransaction createLoginTransaction(
            String state
    ) {

        LtiLoginTransaction transaction =
                new LtiLoginTransaction();

        transaction.setPlatformIssuer(ISSUER);
        transaction.setClientId(CLIENT_ID);

        transaction.setStateHash(
                LtiSecurityUtils.sha256(state)
        );

        transaction.setNonceHash(
                LtiSecurityUtils.sha256("test-nonce")
        );

        transaction.setTargetLinkUri(
                "http://localhost:8080/lti/launch"
        );

        transaction.setExpiresAt(
                Instant.now().plusSeconds(300)
        );

        return transactionRepository.saveAndFlush(transaction);
    }

    private String createSignedToken() throws Exception {

        RSAKey rsaKey = new RSAKeyGenerator(2048)
                .keyID("transaction-test-key")
                .generate();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim(DEPLOYMENT_ID_CLAIM, DEPLOYMENT_ID)
                .issueTime(new Date())
                .expirationTime(
                        Date.from(Instant.now().plusSeconds(300))
                )
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID("transaction-test-key")
                        .build(),
                claims
        );

        signedJWT.sign(new RSASSASigner(rsaKey));

        return signedJWT.serialize();
    }
}
