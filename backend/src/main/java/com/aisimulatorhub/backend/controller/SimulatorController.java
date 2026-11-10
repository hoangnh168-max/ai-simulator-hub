
package com.aisimulatorhub.backend.controller;

import com.aisimulatorhub.backend.model.Simulator;
import com.aisimulatorhub.backend.repository.SimulatorRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/simulators")
@CrossOrigin(origins = "*")
public class SimulatorController {

    private final SimulatorRepository repository;

    public SimulatorController(SimulatorRepository repository) {
        this.repository = repository;
    }

    public record SimulatorRequest(
        @NotBlank String title,
        String description,
        @NotBlank
        @Pattern(regexp = "^https://.+",
            message = "Simulator URL must use HTTPS")
        String simulatorUrl,
        String subject,
        String educationLevel
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Simulator createSimulator(
            @Valid @RequestBody SimulatorRequest request) {

        Simulator simulator = new Simulator();
        simulator.setTitle(request.title());
        simulator.setDescription(request.description());
        simulator.setSimulatorUrl(request.simulatorUrl());
        simulator.setSubject(request.subject());
        simulator.setEducationLevel(request.educationLevel());
        simulator.setStatus("PENDING");

        return repository.save(simulator);
    }

    @GetMapping
    public List<Simulator> getAllSimulators() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Simulator getSimulator(@PathVariable UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Simulator not found"));
    }
}
