
package com.aisimulatorhub.backend.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "lti_jwt_replays",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_lti_jwt_replay_registration_jti",
            columnNames = {"registration_id", "jti_hash"}
        )
    },
    indexes = {
        @Index(
            name = "idx_lti_jwt_replay_expires_at",
            columnList = "expires_at"
        )
    }
)
public class LtiJwtReplay {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "registration_id", nullable = false)
    private UUID registrationId;

    @Column(name = "jti_hash", nullable = false, length = 64)
    private String jtiHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LtiJwtReplay() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(UUID registrationId) {
        this.registrationId = registrationId;
    }

    public String getJtiHash() {
        return jtiHash;
    }

    public void setJtiHash(String jtiHash) {
        this.jtiHash = jtiHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
