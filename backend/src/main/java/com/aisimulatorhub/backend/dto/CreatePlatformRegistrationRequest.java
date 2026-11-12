package com.aisimulatorhub.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePlatformRegistrationRequest(

    @NotBlank
    @Size(max = 200)
    String platformName,

    @NotBlank
    @Size(max = 1000)
    @Pattern(regexp = "^https://\\S+$",
        message = "Platform issuer must use HTTPS")
    String platformIssuer,

    @NotBlank
    @Size(max = 255)
    String clientId,

    @NotBlank
    @Size(max = 255)
    String deploymentId,

    @NotBlank
    @Size(max = 1000)
    @Pattern(regexp = "^https://\\S+$",
        message = "Authorization endpoint must use HTTPS")
    String authorizationEndpoint,

    @NotBlank
    @Size(max = 1000)
    @Pattern(regexp = "^https://\\S+$",
        message = "Token endpoint must use HTTPS")
    String tokenEndpoint,

    @NotBlank
    @Size(max = 1000)
    @Pattern(regexp = "^https://\\S+$",
        message = "Platform JWKS URL must use HTTPS")
    String platformJwksUrl

) {}