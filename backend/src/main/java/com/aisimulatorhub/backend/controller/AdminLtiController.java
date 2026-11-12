package com.aisimulatorhub.backend.controller;

import com.aisimulatorhub.backend.dto.CreatePlatformRegistrationRequest;
import com.aisimulatorhub.backend.dto.PlatformRegistrationResponse;
import com.aisimulatorhub.backend.dto.UpdatePlatformEnabledRequest;
import com.aisimulatorhub.backend.model.PlatformRegistration;
import com.aisimulatorhub.backend.repository.PlatformRegistrationRepository;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/lti/platforms")
public class AdminLtiController {

    private final PlatformRegistrationRepository repository;

    public AdminLtiController(
            PlatformRegistrationRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformRegistrationResponse createPlatform(
            @Valid @RequestBody CreatePlatformRegistrationRequest request) {

        boolean alreadyExists =
            repository.existsByPlatformIssuerAndClientIdAndDeploymentId(
                request.platformIssuer(),
                request.clientId(),
                request.deploymentId()
            );

        if (alreadyExists) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "This platform registration already exists"
            );
        }

        PlatformRegistration platform = new PlatformRegistration();

        platform.setPlatformName(request.platformName());
        platform.setPlatformIssuer(request.platformIssuer());
        platform.setClientId(request.clientId());
        platform.setDeploymentId(request.deploymentId());
        platform.setAuthorizationEndpoint(request.authorizationEndpoint());
        platform.setTokenEndpoint(request.tokenEndpoint());
        platform.setPlatformJwksUrl(request.platformJwksUrl());
        platform.setEnabled(true);

        PlatformRegistration saved = repository.save(platform);

        return PlatformRegistrationResponse.from(saved);
    }

    @GetMapping
    public List<PlatformRegistrationResponse> getAllPlatforms() {
        return repository.findAll()
            .stream()
            .map(PlatformRegistrationResponse::from)
            .toList();
    }

    @GetMapping("/{id}")
    public PlatformRegistrationResponse getPlatform(
            @PathVariable UUID id) {

        PlatformRegistration platform = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Platform registration not found"
            ));

        return PlatformRegistrationResponse.from(platform);
    }

    @PatchMapping("/{id}/enabled")
    public PlatformRegistrationResponse updatePlatformEnabled(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePlatformEnabledRequest request) {

        PlatformRegistration platform = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Platform registration not found"
            ));

        platform.setEnabled(request.enabled());

        PlatformRegistration updated = repository.save(platform);

        return PlatformRegistrationResponse.from(updated);
    }
}