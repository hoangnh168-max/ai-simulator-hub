
package com.aisimulatorhub.backend.lti.service;

import com.aisimulatorhub.backend.lti.dto.ValidatedLtiLaunch;
import com.aisimulatorhub.backend.lti.security.LtiSecurityUtils;
import com.aisimulatorhub.backend.model.PlatformRegistration;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
public class LtiClaimValidationService {

    private static final String LTI_CLAIM_PREFIX =
            "https://purl.imsglobal.org/spec/lti/claim/";

    private static final String DEPLOYMENT_ID =
            LTI_CLAIM_PREFIX + "deployment_id";

    private static final String MESSAGE_TYPE =
            LTI_CLAIM_PREFIX + "message_type";

    private static final String LTI_VERSION =
            LTI_CLAIM_PREFIX + "version";

    private static final String RESOURCE_LINK =
            LTI_CLAIM_PREFIX + "resource_link";

    private static final long CLOCK_SKEW_SECONDS = 60;

    private static final Duration MAX_TOKEN_AGE =
            Duration.ofMinutes(5);

    private static final Duration MAX_TOKEN_LIFETIME =
            Duration.ofMinutes(10);

    public ValidatedLtiLaunch validate(
            SignedJWT signedJWT,
            PlatformRegistration registration,
            String expectedNonceHash
    ) {

        try {

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            String issuer = claims.getIssuer();
            String subject = claims.getSubject();

            // 1. Validate issuer.
            if (!registration.getPlatformIssuer().equals(issuer)) {
                reject("LTI issuer does not match the registered Platform");
            }

            // 2. Validate audience.
            List<String> audience = claims.getAudience();

            if (audience == null
                    || audience.isEmpty()
                    || !audience.contains(registration.getClientId())) {

                reject("LTI audience does not match the registered Client ID");
            }

            // 3. Validate authorized party.
            String authorizedParty = claims.getStringClaim("azp");

            if (authorizedParty != null
                    && !registration.getClientId().equals(authorizedParty)) {

                reject("LTI authorized party does not match the Client ID");
            }

            if (audience.size() > 1 && authorizedParty == null) {
                reject("LTI token with multiple audiences is missing azp");
            }

            // 4. Validate expiration, issued-at, and not-before timestamps.
            Date expiration = claims.getExpirationTime();
            Date issuedAt = claims.getIssueTime();
            Date notBefore = claims.getNotBeforeTime();

            if (expiration == null || issuedAt == null) {
                reject("LTI token is missing exp or iat");
            }

            Instant now = Instant.now();
            Instant expiresAt = expiration.toInstant();
            Instant issuedAtInstant = issuedAt.toInstant();

            if (!expiresAt.isAfter(now)) {
                reject("LTI token has expired");
            }

            if (issuedAtInstant.isAfter(
                    now.plusSeconds(CLOCK_SKEW_SECONDS))) {

                reject("LTI token was issued in the future");
            }

            // 5. Validate not-before (nbf).
            // nbf is optional. When present, allow the same 60-second
            // clock skew already used for the issued-at claim.
            if (notBefore != null) {

                Instant notBeforeInstant = notBefore.toInstant();

                if (notBeforeInstant.isAfter(
                        now.plusSeconds(CLOCK_SKEW_SECONDS))) {

                    reject("LTI token is not yet valid");
                }
            }

            // 6. Validate maximum token age.
            if (issuedAtInstant.isBefore(
                    now.minus(MAX_TOKEN_AGE))) {

                reject("LTI token is too old");
            }

            // 7. Validate token lifetime.
            if (!expiresAt.isAfter(issuedAtInstant)) {
                reject("LTI token expiration is invalid");
            }

            Duration tokenLifetime =
                    Duration.between(issuedAtInstant, expiresAt);

            if (tokenLifetime.compareTo(MAX_TOKEN_LIFETIME) > 0) {
                reject("LTI token lifetime exceeds the permitted limit");
            }

            // 8. Require a unique JWT identifier (jti).
            String jti = claims.getJWTID();

            if (jti == null || jti.isBlank()) {
                reject("LTI token is missing jti");
            }

            if (jti.length() > 10000) {
                reject("LTI token jti exceeds the permitted length");
            }

            // 9. Validate nonce using its stored SHA-256 hash.
            String nonce = claims.getStringClaim("nonce");

            if (nonce == null
                    || nonce.isBlank()
                    || !LtiSecurityUtils.constantTimeEquals(
                            expectedNonceHash,
                            LtiSecurityUtils.sha256(nonce))) {

                reject("LTI nonce does not match the login transaction");
            }

            // 10. Validate deployment ID.
            String deploymentId =
                    claims.getStringClaim(DEPLOYMENT_ID);

            if (!registration.getDeploymentId().equals(deploymentId)) {
                reject("LTI deployment ID does not match the registration");
            }

            // 11. Validate LTI version.
            String ltiVersion =
                    claims.getStringClaim(LTI_VERSION);

            if (!"1.3.0".equals(ltiVersion)) {
                reject("Unsupported LTI version");
            }

            // 12. Validate message type.
            String messageType =
                    claims.getStringClaim(MESSAGE_TYPE);

            boolean resourceLinkLaunch =
                    "LtiResourceLinkRequest".equals(messageType);

            boolean deepLinkingLaunch =
                    "LtiDeepLinkingRequest".equals(messageType);

            if (!resourceLinkLaunch && !deepLinkingLaunch) {
                reject("Unsupported LTI message type");
            }

            // 13. Extract resource_link.id for normal launches.
            String resourceLinkId = null;

            if (resourceLinkLaunch) {

                Object resourceLinkClaim =
                        claims.getClaim(RESOURCE_LINK);

                if (!(resourceLinkClaim instanceof Map<?, ?>)) {
                    reject("LTI resource link claim is missing or invalid");
                }

                Map<?, ?> resourceLink =
                        (Map<?, ?>) resourceLinkClaim;

                Object resourceLinkIdValue =
                        resourceLink.get("id");

                if (!(resourceLinkIdValue instanceof String)
                        || ((String) resourceLinkIdValue).isBlank()) {

                    reject("LTI resource link ID is missing");
                }

                resourceLinkId =
                        (String) resourceLinkIdValue;
            }

            // 14. Validate subject.
            if (subject == null || subject.isBlank()) {
                reject("LTI subject is missing");
            }

            return new ValidatedLtiLaunch(
                    issuer,
                    registration.getClientId(),
                    deploymentId,
                    messageType,
                    ltiVersion,
                    nonce,
                    jti,
                    expiresAt,
                    resourceLinkId,
                    subject,
                    audience,
                    claims.getClaims()
            );

        } catch (ResponseStatusException exception) {

            throw exception;

        } catch (ParseException | IllegalArgumentException exception) {

            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Unable to parse or validate LTI token claims"
            );
        }
    }

    private void reject(String message) {
        throw new ResponseStatusException(FORBIDDEN, message);
    }
}
