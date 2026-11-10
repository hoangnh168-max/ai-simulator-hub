
package com.aisimulatorhub.backend.repository;

import com.aisimulatorhub.backend.model.Simulator;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SimulatorRepository
        extends JpaRepository<Simulator, UUID> {
}
