
package com.aisimulatorhub.backend.lti.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ValidatedLtiLaunch {

    private final String issuer;
    private final String clientId;
    private final String deploymentId;
    private final String messageType;
    private final String ltiVersion;
    private final String nonce;
    private final String jti;
    private final Instant expiresAt;
    private final String resourceLinkId;
    private final String subject;
    private final List<String> audience;
    private final Map<String, Object> claims;

    public ValidatedLtiLaunch(
            String issuer,
            String clientId,
            String deploymentId,
            String messageType,
            String ltiVersion,
            String nonce,
            String jti,
            Instant expiresAt,
            String resourceLinkId,
            String subject,
            List<String> audience,
            Map<String, Object> claims
    ) {
        this.issuer = issuer;
        this.clientId = clientId;
        this.deploymentId = deploymentId;
        this.messageType = messageType;
        this.ltiVersion = ltiVersion;
        this.nonce = nonce;
        this.jti = jti;
        this.expiresAt = expiresAt;
        this.resourceLinkId = resourceLinkId;
        this.subject = subject;
        this.audience = List.copyOf(audience);
        this.claims = Collections.unmodifiableMap(
                new LinkedHashMap<>(claims)
        );
    }

    public String getIssuer() {
        return issuer;
    }

    public String getClientId() {
        return clientId;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getLtiVersion() {
        return ltiVersion;
    }

    public String getNonce() {
        return nonce;
    }

    public String getJti() {
        return jti;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getResourceLinkId() {
        return resourceLinkId;
    }

    public String getSubject() {
        return subject;
    }

    public List<String> getAudience() {
        return audience;
    }

    public Map<String, Object> getClaims() {
        return claims;
    }
}
