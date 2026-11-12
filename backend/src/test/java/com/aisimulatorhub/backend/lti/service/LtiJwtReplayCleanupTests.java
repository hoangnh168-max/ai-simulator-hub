
package com.aisimulatorhub.backend.lti.service;

import com.aisimulatorhub.backend.lti.security.LtiSecurityUtils;
import com.aisimulatorhub.backend.repository.LtiJwtReplayRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LtiJwtReplayCleanupTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LtiJwtReplayRepository replayRepository;

    @Autowired
    private LtiJwtReplayService replayService;

    @Test
    void cleanupShouldDeleteExpiredRecordsAndPreserveValidRecords() {

        UUID registrationId = UUID.randomUUID();
        UUID expiredId = UUID.randomUUID();
        UUID validId = UUID.randomUUID();

        Instant now = Instant.now();

        String expiredHash =
                LtiSecurityUtils.sha256("expired-" + UUID.randomUUID());

        String validHash =
                LtiSecurityUtils.sha256("valid-" + UUID.randomUUID());

        jdbcTemplate.update(
                """
                INSERT INTO lti_jwt_replays (
                    id,
                    registration_id,
                    jti_hash,
                    expires_at,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                expiredId,
                registrationId,
                expiredHash,
                Timestamp.from(now.minusSeconds(120)),
                Timestamp.from(now)
        );

        jdbcTemplate.update(
                """
                INSERT INTO lti_jwt_replays (
                    id,
                    registration_id,
                    jti_hash,
                    expires_at,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                validId,
                registrationId,
                validHash,
                Timestamp.from(now.plusSeconds(3600)),
                Timestamp.from(now)
        );

        try {
            assertTrue(replayRepository.existsById(expiredId));
            assertTrue(replayRepository.existsById(validId));

            int deleted = replayService.removeExpiredRecords();

            assertTrue(
                    deleted >= 1,
                    "At least one expired replay record should be deleted"
            );

            assertFalse(
                    replayRepository.existsById(expiredId),
                    "Expired replay record should be deleted"
            );

            assertTrue(
                    replayRepository.existsById(validId),
                    "Unexpired replay record should remain"
            );

        } finally {
            jdbcTemplate.update(
                    "DELETE FROM lti_jwt_replays WHERE id IN (?, ?)",
                    expiredId,
                    validId
            );
        }
    }
}
