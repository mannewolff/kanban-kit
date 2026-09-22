package org.mwolff.manban.ratelimit.infrastructure;

import java.time.Clock;
import java.time.Duration;
import org.mwolff.manban.ratelimit.infrastructure.persistence.OverloadRejectionTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Räumt die Abweisungs-Aggregate nach 90 Tagen (Issue #1000, Plan #995 E23), nach dem Muster von
 * {@code OutboxRetentionJob}: am Aufräum-Schalter {@code manban.cleanup.enabled} und im Rhythmus
 * {@code manban.cleanup.cron} der übrigen Aufräumjobs.
 *
 * <p>Jedes Modul räumt seine eigene Tabelle — deshalb liegt der Job hier und nicht bei einem
 * anderen Aufräumjob. Die Frist ist fest: Eine Tabelle ohne Frist wäre ein bekannter Mangel, und
 * eine Stellschraube dafür verlangt kein Kriterium.
 */
@Component
@ConditionalOnProperty(name = "manban.cleanup.enabled", havingValue = "true", matchIfMissing = true)
class RejectionRetentionJob {

  /** Aufbewahrung der Aggregate (E23). */
  static final Duration RETENTION = Duration.ofDays(90);

  private static final Logger log = LoggerFactory.getLogger(RejectionRetentionJob.class);

  private final OverloadRejectionTable table;
  private final Clock clock;

  RejectionRetentionJob(OverloadRejectionTable table, Clock clock) {
    this.table = table;
    this.clock = clock;
  }

  @Scheduled(cron = "${manban.cleanup.cron:0 0 * * * *}")
  void run() {
    int deleted = table.deleteOlderThan(clock.instant().minus(RETENTION));
    if (deleted > 0) {
      log.info("Überlast-Abweisungen: {} Stunden-Aggregate älter als 90 Tage gelöscht", deleted);
    }
  }
}
