
package com.aisimulatorhub.backend.lti.service;

import com.aisimulatorhub.backend.lti.dto.ValidatedLtiLaunch;
import com.aisimulatorhub.backend.lti.security.LtiSecurityUtils;
import com.aisimulatorhub.backend.model.LtiLoginTransaction;
import com.aisimulatorhub.backend.model.PlatformRegistration;
import com.aisimulatorhub.backend.repository.LtiLoginTransactionRepository;
import com.aisimulatorhub.backend.repository.PlatformRegistrationRepository;

import com.nimbusds.jwt.SignedJWT;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
public class LtiLaunchService {

    private static final String DEPLOYMENT_ID_CLAIM =
            "https://purl.imsglobal.org/spec/lti/claim/deployment_id";

    private final LtiLoginTransactionRepository transactionRepository;
    private final PlatformRegistrationRepository platformRepository;
    private final LtiTokenValidationService tokenValidationService;
    private final LtiClaimValidationService claimValidationService;
    private final LtiJwtReplayService jwtReplayService;
    private final String platformBaseUrl;

    public LtiLaunchService(
            LtiLoginTransactionRepository transactionRepository,
            PlatformRegistrationRepository platformRepository,
            LtiTokenValidationService tokenValidationService,
            LtiClaimValidationService claimValidationService,
            LtiJwtReplayService jwtReplayService,
            LtiPlatformBaseUrlValidator baseUrlValidator
    ) {
        this.transactionRepository = transactionRepository;
        this.platformRepository = platformRepository;
        this.tokenValidationService = tokenValidationService;
        this.claimValidationService = claimValidationService;
        this.jwtReplayService = jwtReplayService;
        this.platformBaseUrl = baseUrlValidator.getValidatedBaseUrl();
    }

    @Transactional
    public ValidatedLtiLaunch processLaunch(
            String idToken,
            String state
    ) {

        // 1. Validate incoming parameters.
        if (idToken == null || idToken.isBlank()
                || state == null || state.isBlank()
                || idToken.length() > 50000
                || state.length() > 200) {

            reject("Invalid LTI launch parameters");
        }

        // 2. Find the transaction using the hashed state.
        String stateHash = LtiSecurityUtils.sha256(state);

        LtiLoginTransaction transaction =
                transactionRepository.findByStateHash(stateHash)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        FORBIDDEN,
                                        "LTI login transaction was not found"
                                )
                        );

        // 3. Check transaction status.
        Instant now = Instant.now();

        if (transaction.getConsumedAt() != null) {
            reject("LTI login transaction has already been consumed");
        }

        if (transaction.getExpiresAt() == null
                || !transaction.getExpiresAt().isAfter(now)) {

            reject("LTI login transaction has expired");
        }

        // 4. Parse the signed token to identify its deployment.
        // The deployment ID is untrusted at this stage.
        SignedJWT signedJWT;

        try {
            signedJWT = SignedJWT.parse(idToken);

        } catch (Exception exception) {
            reject("Invalid LTI ID token format");
            return null;
        }

        String deploymentId;

        try {
            Object claim = signedJWT.getJWTClaimsSet()
                    .getClaim(DEPLOYMENT_ID_CLAIM);

            if (!(claim instanceof String value)
                    || value.isBlank()) {

                reject("Missing or invalid deployment ID");
                return null;
            }

            deploymentId = value;

        } catch (Exception exception) {
            reject("Unable to read deployment ID");
            return null;
        }

        // 5. Resolve the exact enabled platform registration.
        PlatformRegistration registration =
                platformRepository
                        .findByPlatformIssuerAndClientIdAndDeploymentIdAndEnabledTrue(
                                transaction.getPlatformIssuer(),
                                transaction.getClientId(),
                                deploymentId
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        FORBIDDEN,
                                        "No enabled platform registration matches this LTI launch"
                                )
                        );

        // 6. Verify the token signature against the registered platform JWKS.
        // Use the actual token-validation service API.
        SignedJWT verifiedJWT =
                tokenValidationService.verifySignature(
                        idToken,
                        registration
                );

        // 7. Validate LTI claims using the verified token,
        // registered platform, and nonce hash stored in the transaction.
        ValidatedLtiLaunch validatedLaunch =
                claimValidationService.validate(
                        verifiedJWT,
                        registration,
                        transaction.getNonceHash()
                );

        // 8. Validate the stored target link.
        String expectedTargetLinkUri =
                normalizeBaseUrl(platformBaseUrl) + "/lti/launch";

        if (!expectedTargetLinkUri.equals(
                transaction.getTargetLinkUri())) {

            reject("LTI target link URI does not match platform configuration");
        }

        // 9. Register the JWT ID to prevent replay.
        jwtReplayService.registerToken(
                registration,
                validatedLaunch.getJti(),
                validatedLaunch.getExpiresAt()
        );

        // 10. Atomically consume the login transaction.
        int consumed =
                transactionRepository.consumeIfValid(
                        transaction.getId(),
                        now
                );

        if (consumed != 1) {
            reject("LTI login transaction is invalid or already consumed");
        }

        return validatedLaunch;
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value.trim();

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(
                    0,
                    normalized.length() - 1
            );
        }

        return normalized;
    }

    private void reject(String message) {
        throw new ResponseStatusException(FORBIDDEN, message);
    }
}
