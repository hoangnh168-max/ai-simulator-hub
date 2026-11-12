package com.aisimulatorhub.backend.lti.service;

import com.aisimulatorhub.backend.repository.LtiLoginTransactionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class LtiLoginTransactionCleanupService {

    private final LtiLoginTransactionRepository repository;

    public LtiLoginTransactionCleanupService(
            LtiLoginTransactionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int removeExpiredTransactions() {
        return repository.deleteExpired(Instant.now());
    }
}