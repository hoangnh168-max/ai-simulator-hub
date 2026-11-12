
package com.aisimulatorhub.backend.repository;

import com.aisimulatorhub.backend.model.LtiLoginTransaction;

import jakarta.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LtiLoginTransactionRepository
        extends JpaRepository<LtiLoginTransaction, UUID> {

    Optional<LtiLoginTransaction> findByStateHash(String stateHash);

    @Modifying
    @Transactional
    @Query("""
        UPDATE LtiLoginTransaction transaction
        SET transaction.consumedAt = :now
        WHERE transaction.id = :id
          AND transaction.consumedAt IS NULL
          AND transaction.expiresAt > :now
        """)
    int consumeIfValid(
        @Param("id") UUID id,
        @Param("now") Instant now
    );

    @Modifying
    @Transactional
    @Query("""
        DELETE FROM LtiLoginTransaction transaction
        WHERE transaction.expiresAt < :now
        """)
    int deleteExpired(@Param("now") Instant now);
}
