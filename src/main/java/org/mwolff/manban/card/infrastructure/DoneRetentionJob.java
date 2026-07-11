package org.mwolff.manban.card.infrastructure;

import java.time.Clock;
import org.mwolff.manban.card.application.CleanupProperties;
import org.mwolff.manban.card.application.DoneRetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Geplanter Aufräum-Job: ruft den {@link DoneRetentionService} auf (separater Bean →
 * keine Self-Invocation, {@code @Transactional} greift). Abschaltbar über
 * {@code manban.cleanup.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "manban.cleanup.enabled", havingValue = "true", matchIfMissing = true)
class DoneRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(DoneRetentionJob.class);

    private final DoneRetentionService retention;
    private final CleanupProperties properties;
    private final Clock clock;

    DoneRetentionJob(DoneRetentionService retention, CleanupProperties properties, Clock clock) {
        this.retention = retention;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${manban.cleanup.cron:0 0 * * * *}")
    void run() {
        int archived = retention.archiveExpiredDoneCards(clock.instant(), properties.doneRetentionDays());
        if (archived > 0) {
            log.info("Done-Retention: {} abgelaufene Karten archiviert", archived);
        }
    }
}
