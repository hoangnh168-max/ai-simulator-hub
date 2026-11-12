
package com.aisimulatorhub.backend.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "lti_login_transactions",
    indexes = {
        @Index(
            name = "idx_lti_login_transaction_expiry",
            columnList = "expires_at"
        )
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_lti_login_transaction_state_hash",
            columnNames = "state_hash"
        )
    }
)
public class LtiLoginTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "platform_issuer", nullable = false, length = 1000)
    private String platformIssuer;

    @Column(name = "client_id", nullable = false, length = 255)
    private String clientId;

    @Column(name = "state_hash", nullable = false, length = 64)
    private String stateHash;

    @Column(name = "nonce_hash", nullable = false, length = 64)
    private String nonceHash;

    @Column(name = "target_link_uri", nullable = false, length = 1000)
    private String targetLinkUri;

    @Column(name = "login_hint", length = 2000)
    private String loginHint;

    @Column(name = "lti_message_hint", length = 4000)
    private String ltiMessageHint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getPlatformIssuer() {
        return platformIssuer;
    }

    public void setPlatformIssuer(String platformIssuer) {
        this.platformIssuer = platformIssuer;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getStateHash() {
        return stateHash;
    }

    public void setStateHash(String stateHash) {
        this.stateHash = stateHash;
    }

    public String getNonceHash() {
        return nonceHash;
    }

    public void setNonceHash(String nonceHash) {
        this.nonceHash = nonceHash;
    }

    public String getTargetLinkUri() {
        return targetLinkUri;
    }

    public void setTargetLinkUri(String targetLinkUri) {
        this.targetLinkUri = targetLinkUri;
    }

    public String getLoginHint() {
        return loginHint;
    }

    public void setLoginHint(String loginHint) {
        this.loginHint = loginHint;
    }

    public String getLtiMessageHint() {
        return ltiMessageHint;
    }

    public void setLtiMessageHint(String ltiMessageHint) {
        this.ltiMessageHint = ltiMessageHint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }
}
