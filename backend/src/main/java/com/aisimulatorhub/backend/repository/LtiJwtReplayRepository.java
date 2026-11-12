
package com.aisimulatorhub.backend.repository;

import com.aisimulatorhub.backend.model.LtiJwtReplay;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

public interface LtiJwtReplayRepository
        extends JpaRepository<LtiJwtReplay, UUID> {

    @Modifying
    @Transactional
    @Query(
        value = """
            INSERT INTO lti_jwt_replays (
                id,
                registration_id,
                jti_hash,
                expires_at,
                created_at
            )
            VALUES (
                :id,
                :registrationId,
                :jtiHash,
                :expiresAt,
                :createdAt
            )
            ON CONFLICT (registration_id, jti_hash)
            DO NOTHING
            """,
        nativeQuery = true
    )
    int insertIfAbsent(
        @Param("id") UUID id,
        @Param("registrationId") UUID registrationId,
        @Param("jtiHash") String jtiHash,
        @Param("expiresAt") Instant expiresAt,
        @Param("createdAt") Instant createdAt
    );

    @Modifying
    @Transactional
    @Query("""
        DELETE FROM LtiJwtReplay replay
        WHERE replay.expiresAt < :now
        """)
    int deleteExpired(@Param("now") Instant now);
}
