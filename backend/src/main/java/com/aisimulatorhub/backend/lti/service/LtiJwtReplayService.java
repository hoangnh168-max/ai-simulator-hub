
package com.aisimulatorhub.backend.lti.service;

import com.aisimulatorhub.backend.lti.security.LtiSecurityUtils;
import com.aisimulatorhub.backend.model.PlatformRegistration;
import com.aisimulatorhub.backend.repository.LtiJwtReplayRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
public class LtiJwtReplayService {

    private static final long RETENTION_GRACE_SECONDS = 60;

    private final LtiJwtReplayRepository replayRepository;

    public LtiJwtReplayService(
            LtiJwtReplayRepository replayRepository
    ) {
        this.replayRepository = replayRepository;
    }

    @Transactional
    public void registerToken(
            PlatformRegistration registration,
            String jti,
            Instant tokenExpiresAt
    ) {

        if (registration == null
                || registration.getId() == null
                || jti == null
                || jti.isBlank()
                || tokenExpiresAt == null) {

            reject("Invalid LTI token replay protection parameters");
        }

        if (jti.length() > 10000) {
            reject("LTI token identifier exceeds the permitted length");
        }

        Instant now = Instant.now();

        if (!tokenExpiresAt.isAfter(now)) {
            reject("LTI token has expired");
        }

        String jtiHash = LtiSecurityUtils.sha256(jti);

        Instant retentionUntil =
                tokenExpiresAt.plusSeconds(RETENTION_GRACE_SECONDS);

        int inserted = replayRepository.insertIfAbsent(
                UUID.randomUUID(),
                registration.getId(),
                jtiHash,
                retentionUntil,
                now
        );

        if (inserted != 1) {
            reject("LTI token has already been used");
        }
    }

    @Transactional
    public int removeExpiredRecords() {
        return replayRepository.deleteExpired(Instant.now());
    }

    private void reject(String message) {
        throw new ResponseStatusException(FORBIDDEN, message);
    }
}
