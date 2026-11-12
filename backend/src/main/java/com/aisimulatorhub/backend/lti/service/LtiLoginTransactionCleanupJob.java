package com.aisimulatorhub.backend.lti.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LtiLoginTransactionCleanupJob {

    private static final Logger log =
            LoggerFactory.getLogger(LtiLoginTransactionCleanupJob.class);

    private final LtiLoginTransactionCleanupService cleanupService;

    public LtiLoginTransactionCleanupJob(
            LtiLoginTransactionCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 90_000)
    public void cleanExpiredTransactions() {
        int deleted = cleanupService.removeExpiredTransactions();

        if (deleted > 0) {
            log.info("Removed {} expired LTI login transactions", deleted);
        }
    }
}