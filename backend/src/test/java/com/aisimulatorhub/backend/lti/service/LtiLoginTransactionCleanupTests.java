package com.aisimulatorhub.backend.lti.service;

import com.aisimulatorhub.backend.repository.LtiLoginTransactionRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LtiLoginTransactionCleanupTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LtiLoginTransactionRepository transactionRepository;

    @Autowired
    private LtiLoginTransactionCleanupService cleanupService;

    @Test
    void cleanupShouldDeleteExpiredTransactionsAndPreserveValidTransactions() {

        UUID expiredId = UUID.randomUUID();
        UUID validId = UUID.randomUUID();

        Instant now = Instant.now();

        insertTransaction(
                expiredId,
                "expired-state-" + UUID.randomUUID(),
                "expired-nonce-" + UUID.randomUUID(),
                now.minusSeconds(120),
                now.minusSeconds(60)
        );

        insertTransaction(
                validId,
                "valid-state-" + UUID.randomUUID(),
                "valid-nonce-" + UUID.randomUUID(),
                now,
                now.plusSeconds(3600)
        );

        try {
            assertTrue(transactionRepository.existsById(expiredId));
            assertTrue(transactionRepository.existsById(validId));

            int deleted = cleanupService.removeExpiredTransactions();

            assertTrue(
                    deleted >= 1,
                    "At least one expired login transaction should be deleted"
            );

            assertFalse(
                    transactionRepository.existsById(expiredId),
                    "Expired login transaction should be deleted"
            );

            assertTrue(
                    transactionRepository.existsById(validId),
                    "Unexpired login transaction should remain"
            );

        } finally {
            jdbcTemplate.update(
                    "DELETE FROM lti_login_transactions WHERE id IN (?, ?)",
                    expiredId,
                    validId
            );
        }
    }

    private void insertTransaction(
            UUID id,
            String state,
            String nonce,
            Instant createdAt,
            Instant expiresAt) {

        jdbcTemplate.update(
                """
                INSERT INTO lti_login_transactions (
                    id,
                    platform_issuer,
                    client_id,
                    state_hash,
                    nonce_hash,
                    target_link_uri,
                    created_at,
                    expires_at,
                    consumed_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL)
                """,
                id,
                "https://platform.example.com",
                "cleanup-test-client",
                com.aisimulatorhub.backend.lti.security.LtiSecurityUtils.sha256(state),
                com.aisimulatorhub.backend.lti.security.LtiSecurityUtils.sha256(nonce),
                "https://simulator.example.com/launch",
                Timestamp.from(createdAt),
                Timestamp.from(expiresAt)
        );
    }
}