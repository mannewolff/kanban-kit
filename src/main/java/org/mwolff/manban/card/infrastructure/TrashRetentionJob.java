package org.mwolff.manban.card.infrastructure;

import java.time.Clock;
import org.mwolff.manban.card.application.CleanupProperties;
import org.mwolff.manban.card.application.TrashRetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Geplanter Aufräum-Job: löscht abgelaufene Papierkorb-Karten endgültig (ruft den separaten {@link
 * TrashRetentionService}-Bean auf → keine Self-Invocation). Abschaltbar über {@code
 * manban.cleanup.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "manban.cleanup.enabled", havingValue = "true", matchIfMissing = true)
class TrashRetentionJob {

  private static final Logger log = LoggerFactory.getLogger(TrashRetentionJob.class);

  private final TrashRetentionService retention;
  private final CleanupProperties properties;
  private final Clock clock;

  TrashRetentionJob(TrashRetentionService retention, CleanupProperties properties, Clock clock) {
    this.retention = retention;
    this.properties = properties;
    this.clock = clock;
  }

  @Scheduled(cron = "${manban.cleanup.cron:0 0 * * * *}")
  void run() {
    int purged = retention.purgeExpiredTrash(clock.instant(), properties.trashRetentionDays());
    if (purged > 0) {
      log.info("Papierkorb-Retention: {} abgelaufene Karten endgültig gelöscht", purged);
    }
  }
}
