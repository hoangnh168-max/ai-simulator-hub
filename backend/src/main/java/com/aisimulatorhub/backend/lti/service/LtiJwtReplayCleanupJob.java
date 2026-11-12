
package com.aisimulatorhub.backend.lti.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LtiJwtReplayCleanupJob {

    private static final Logger logger =
            LoggerFactory.getLogger(LtiJwtReplayCleanupJob.class);

    private final LtiJwtReplayService replayService;

    public LtiJwtReplayCleanupJob(
            LtiJwtReplayService replayService
    ) {
        this.replayService = replayService;
    }

    @Scheduled(
        fixedDelay = 3_600_000,
        initialDelay = 60_000
    )
    public void cleanupExpiredReplayRecords() {

        int deleted = replayService.removeExpiredRecords();

        if (deleted > 0) {
            logger.info(
                    "Removed {} expired LTI JWT replay records",
                    deleted
            );
        }
    }
}
