package com.portfolio.ticketing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class HoldExpiryJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(HoldExpiryJob.class);

    private final HoldService holds;

    public HoldExpiryJob(HoldService holds) {
        this.holds = holds;
    }

    @Scheduled(fixedDelayString = "${app.holds.expiry-interval:PT1S}")
    public void releaseExpiredHolds() {
        int count = holds.expireBatch();
        if (count > 0) {
            LOGGER.info("Released {} expired seat holds", count);
        }
    }
}
