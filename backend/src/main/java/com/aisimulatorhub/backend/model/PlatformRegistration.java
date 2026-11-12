package com.aisimulatorhub.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "lti_platform_registrations",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_lti_platform_registration_identity",
            columnNames = {
                "platform_issuer",
                "client_id",
                "deployment_id"
            }
        )
    }
)
public class PlatformRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "platform_name", nullable = false, length = 200)
    private String platformName;

    @Column(name = "platform_issuer", nullable = false, length = 1000)
    private String platformIssuer;

    @Column(name = "client_id", nullable = false, length = 255)
    private String clientId;

    @Column(name = "deployment_id", nullable = false, length = 255)
    private String deploymentId;

    @Column(name = "authorization_endpoint", nullable = false, length = 1000)
    private String authorizationEndpoint;

    @Column(name = "token_endpoint", nullable = false, length = 1000)
    private String tokenEndpoint;

    @Column(name = "platform_jwks_url", nullable = false, length = 1000)
    private String platformJwksUrl;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPlatformName() {
        return platformName;
    }

    public void setPlatformName(String platformName) {
        this.platformName = platformName;
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

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getAuthorizationEndpoint() {
        return authorizationEndpoint;
    }

    public void setAuthorizationEndpoint(String authorizationEndpoint) {
        this.authorizationEndpoint = authorizationEndpoint;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public void setTokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }

    public String getPlatformJwksUrl() {
        return platformJwksUrl;
    }

    public void setPlatformJwksUrl(String platformJwksUrl) {
        this.platformJwksUrl = platformJwksUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
