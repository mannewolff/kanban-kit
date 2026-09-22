package org.mwolff.manban.kanbancompat.infrastructure;

import java.time.Clock;
import java.time.Duration;
import org.mwolff.manban.kanbancompat.application.IdempotencyRecordStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Räumt Idempotenz-Schlüssel nach 24 Stunden (Issue #1001, Plan #995 E9), nach dem Muster von
 * {@code OutboxRetentionJob}: am Aufräum-Schalter {@code manban.cleanup.enabled} und im Rhythmus
 * {@code manban.cleanup.cron}.
 *
 * <p>24 Stunden, damit auch ein <em>von Hand</em> wiederholter Auftrag noch auf seinen Schlüssel
 * trifft. Weil der Job stündlich läuft, gilt ein Schlüssel mindestens 24 Stunden und höchstens eine
 * Stunde länger — die Zusage ist die Untergrenze.
 */
@Component
@ConditionalOnProperty(name = "manban.cleanup.enabled", havingValue = "true", matchIfMissing = true)
class IdempotencyRetentionJob {

  /** Haltbarkeit eines Schlüssels (E9). */
  static final Duration RETENTION = Duration.ofHours(24);

  private static final Logger log = LoggerFactory.getLogger(IdempotencyRetentionJob.class);

  private final IdempotencyRecordStore store;
  private final Clock clock;

  IdempotencyRetentionJob(IdempotencyRecordStore store, Clock clock) {
    this.store = store;
    this.clock = clock;
  }

  @Scheduled(cron = "${manban.cleanup.cron:0 0 * * * *}")
  void run() {
    int deleted = store.deleteOlderThan(clock.instant().minus(RETENTION));
    if (deleted > 0) {
      log.info("Idempotenz: {} Idempotenz-Schlüssel älter als 24 Stunden gelöscht", deleted);
    }
  }
}
