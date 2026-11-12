package com.aisimulatorhub.backend.dto;

import jakarta.validation.constraints.NotNull;

public record UpdatePlatformEnabledRequest(
    @NotNull Boolean enabled
) {}