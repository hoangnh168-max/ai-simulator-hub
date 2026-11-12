
package com.aisimulatorhub.backend.repository;

import com.aisimulatorhub.backend.model.PlatformRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformRegistrationRepository
        extends JpaRepository<PlatformRegistration, UUID> {

    Optional<PlatformRegistration>
        findByPlatformIssuerAndClientIdAndDeploymentId(
            String platformIssuer,
            String clientId,
            String deploymentId
        );

    Optional<PlatformRegistration>
        findByPlatformIssuerAndClientIdAndDeploymentIdAndEnabledTrue(
            String platformIssuer,
            String clientId,
            String deploymentId
        );

    List<PlatformRegistration> findByEnabledTrue();

    List<PlatformRegistration>
        findByPlatformIssuerAndClientIdAndEnabledTrue(
            String platformIssuer,
            String clientId
        );

    boolean existsByPlatformIssuerAndClientIdAndDeploymentId(
        String platformIssuer,
        String clientId,
        String deploymentId
    );

    boolean existsByPlatformIssuerAndClientIdAndDeploymentIdAndEnabledTrue(
        String platformIssuer,
        String clientId,
        String deploymentId
    );
}
