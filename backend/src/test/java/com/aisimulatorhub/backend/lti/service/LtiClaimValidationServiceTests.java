
package com.aisimulatorhub.backend.lti.service;

import com.aisimulatorhub.backend.lti.dto.ValidatedLtiLaunch;
import com.aisimulatorhub.backend.model.PlatformRegistration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LtiClaimValidationServiceTests {

    private static final String ISSUER =
            "https://platform-test.example";

    private static final String CLIENT_ID =
            "test-client";

    private static final String DEPLOYMENT_ID =
            "test-deployment";

    private static final String NONCE =
            "test-nonce";

    private static final String DEPLOYMENT_ID_CLAIM =
            "https://purl.imsglobal.org/spec/lti/claim/deployment_id";

    private static final String MESSAGE_TYPE_CLAIM =
            "https://purl.imsglobal.org/spec/lti/claim/message_type";

    private static final String LTI_VERSION_CLAIM =
            "https://purl.imsglobal.org/spec/lti/claim/version";

    private static final String RESOURCE_LINK_CLAIM =
            "https://purl.imsglobal.org/spec/lti/claim/resource_link";

    private static RSAKey signingKey;

    private LtiClaimValidationService validationService;

    private PlatformRegistration registration;

    @BeforeAll
    static void generateSigningKey() throws Exception {
        signingKey = new RSAKeyGenerator(2048)
                .keyID("claim-validation-test-key")
                .generate();
    }

    @BeforeEach
    void setUp() {
        validationService = new LtiClaimValidationService();

        registration = new PlatformRegistration();
        registration.setPlatformName("Test Platform");
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
    }

    @Test
    void tokenWithoutNbfShouldBeAccepted() throws Exception {
        SignedJWT token = createToken(null);

        ValidatedLtiLaunch result = validate(token);

        assertNotNull(result);
        assertEquals(CLIENT_ID, result.getClientId());
    }

    @Test
    void tokenWithPastNbfShouldBeAccepted() throws Exception {
        SignedJWT token = createToken(
                Instant.now().minusSeconds(30)
        );

        assertNotNull(validate(token));
    }

    @Test
    void tokenWithNbfThirtySecondsInFutureShouldBeAccepted()
            throws Exception {

        SignedJWT token = createToken(
                Instant.now().plusSeconds(30)
        );

        assertNotNull(validate(token));
    }

    @Test
    void tokenWithNbfExactlySixtySecondsInFutureShouldBeAccepted()
            throws Exception {

        SignedJWT token = createToken(
                Instant.now().plusSeconds(60)
        );

        assertNotNull(validate(token));
    }

    @Test
    void tokenWithNbfMoreThanSixtySecondsInFutureShouldBeRejected()
            throws Exception {

        SignedJWT token = createToken(
                Instant.now().plusSeconds(120)
        );

        assertThrows(
                ResponseStatusException.class,
                () -> validate(token)
        );
    }

    @Test
    void expiredTokenShouldBeRejected() throws Exception {
        SignedJWT token = createToken(
                null,
                Instant.now().minusSeconds(60),
                Instant.now().minusSeconds(120)
        );

        assertThrows(
                ResponseStatusException.class,
                () -> validate(token)
        );
    }

    private ValidatedLtiLaunch validate(SignedJWT token) {
        return validationService.validate(
                token,
                registration,
                com.aisimulatorhub.backend.lti.security.LtiSecurityUtils
                        .sha256(NONCE)
        );
    }

    private SignedJWT createToken(Instant notBefore)
            throws Exception {

        Instant now = Instant.now();

        return createToken(
                notBefore,
                now.plusSeconds(300),
                now.minusSeconds(10)
        );
    }

    private SignedJWT createToken(
            Instant notBefore,
            Instant expiration,
            Instant issuedAt
    ) throws Exception {

        JWTClaimsSet.Builder claimsBuilder =
                new JWTClaimsSet.Builder()
                        .issuer(ISSUER)
                        .subject("test-subject-" + UUID.randomUUID())
                        .audience(List.of(CLIENT_ID))
                        .issueTime(Date.from(issuedAt))
                        .expirationTime(Date.from(expiration))
                        .jwtID("test-jti-" + UUID.randomUUID())
                        .claim("nonce", NONCE)
                        .claim(DEPLOYMENT_ID_CLAIM, DEPLOYMENT_ID)
                        .claim(MESSAGE_TYPE_CLAIM, "LtiResourceLinkRequest")
                        .claim(LTI_VERSION_CLAIM, "1.3.0")
                        .claim(
                                RESOURCE_LINK_CLAIM,
                                java.util.Map.of("id", "test-resource")
                        );

        if (notBefore != null) {
            claimsBuilder.notBeforeTime(Date.from(notBefore));
        }

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID("claim-validation-test-key")
                        .build(),
                claimsBuilder.build()
        );

        signedJWT.sign(new RSASSASigner(signingKey));

        return signedJWT;
    }
}
