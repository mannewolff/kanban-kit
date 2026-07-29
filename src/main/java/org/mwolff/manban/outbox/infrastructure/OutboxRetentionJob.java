package org.mwolff.manban.outbox.infrastructure;

import java.time.Clock;
import org.mwolff.manban.outbox.application.OutboxProperties;
import org.mwolff.manban.outbox.application.OutboxRetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Geplanter Aufräum-Job für erledigte Outbox-Einträge (Issue #501). Läuft im selben Rhythmus wie
 * die übrigen Retention-Jobs ({@code manban.cleanup.cron}) — eine eigene Zeitplanung dafür wäre ein
 * zweiter Ort, an dem dasselbe eingestellt werden müsste.
 */
@Component
@ConditionalOnProperty(name = "manban.cleanup.enabled", havingValue = "true", matchIfMissing = true)
class OutboxRetentionJob {

  private static final Logger log = LoggerFactory.getLogger(OutboxRetentionJob.class);

  private final OutboxRetentionService retention;
  private final OutboxProperties properties;
  private final Clock clock;

  OutboxRetentionJob(OutboxRetentionService retention, OutboxProperties properties, Clock clock) {
    this.retention = retention;
    this.properties = properties;
    this.clock = clock;
  }

  @Scheduled(cron = "${manban.cleanup.cron:0 0 * * * *}")
  void run() {
    int purged = retention.purgeCompleted(clock.instant(), properties.completedRetentionDays());
    if (purged > 0) {
      log.info("Outbox-Retention: {} erledigte Einträge gelöscht", purged);
    }
  }
}
