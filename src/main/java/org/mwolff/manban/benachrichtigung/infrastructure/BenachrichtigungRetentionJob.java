package org.mwolff.manban.benachrichtigung.infrastructure;

import java.time.Clock;
import java.time.Duration;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Geplanter Aufräum-Job für Anlässe und Zustellungen (Issue #2081, Plan #2070 E14). Läuft im selben
 * Rhythmus wie die übrigen Retention-Jobs ({@code manban.cleanup.cron}).
 */
@Component
@ConditionalOnProperty(name = "manban.cleanup.enabled", havingValue = "true", matchIfMissing = true)
class BenachrichtigungRetentionJob {

  private static final Logger log = LoggerFactory.getLogger(BenachrichtigungRetentionJob.class);

  private final AnlassRepository anlaesse;
  private final BenachrichtigungProperties properties;
  private final Clock clock;

  BenachrichtigungRetentionJob(
      AnlassRepository anlaesse, BenachrichtigungProperties properties, Clock clock) {
    this.anlaesse = anlaesse;
    this.properties = properties;
    this.clock = clock;
  }

  @Scheduled(cron = "${manban.cleanup.cron:0 0 * * * *}")
  void run() {
    int geloescht =
        anlaesse.loescheAelterAls(
            clock.instant().minus(Duration.ofDays(properties.retentionDays())));
    if (geloescht > 0) {
      log.info("Benachrichtigung-Retention: {} Anlässe samt Zustellungen gelöscht", geloescht);
    }
  }
}
