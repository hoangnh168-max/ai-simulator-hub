package com.aisimulatorhub.backend.dto;

import com.aisimulatorhub.backend.model.PlatformRegistration;

import java.time.LocalDateTime;
import java.util.UUID;

public record PlatformRegistrationResponse(
    UUID id,
    String platformName,
    String platformIssuer,
    String clientId,
    String deploymentId,
    String authorizationEndpoint,
    String tokenEndpoint,
    String platformJwksUrl,
    boolean enabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static PlatformRegistrationResponse from(
            PlatformRegistration platform) {

        return new PlatformRegistrationResponse(
            platform.getId(),
            platform.getPlatformName(),
            platform.getPlatformIssuer(),
            platform.getClientId(),
            platform.getDeploymentId(),
            platform.getAuthorizationEndpoint(),
            platform.getTokenEndpoint(),
            platform.getPlatformJwksUrl(),
            platform.isEnabled(),
            platform.getCreatedAt(),
            platform.getUpdatedAt()
        );
    }
}