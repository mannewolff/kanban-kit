package org.mwolff.manban.card.infrastructure;

import java.time.Clock;
import org.mwolff.manban.card.application.DoneRetentionService;
import org.mwolff.manban.card.application.DoneRetentionSettingService;
import org.mwolff.manban.card.application.VorhabenArchivierung;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Geplanter Aufräum-Job: ruft den {@link DoneRetentionService} auf (separater Bean → keine
 * Self-Invocation, {@code @Transactional} greift). Abschaltbar über {@code
 * manban.cleanup.enabled=false}.
 *
 * <p>Nach der Karten-Archivierung gleicht der Job unbedingt die Vorhaben ab ({@link
 * VorhabenArchivierung}) — auch bei Aufbewahrung 0, wenn Karten nur von Hand archiviert werden
 * (Issue #1494, AK 3). Die Reihenfolge sorgt dafür, dass eben archivierte Karten schon nicht mehr
 * zählen; beide Schritte laufen in getrennten Transaktionen.
 */
@Component
@ConditionalOnProperty(name = "manban.cleanup.enabled", havingValue = "true", matchIfMissing = true)
class DoneRetentionJob {

  private static final Logger log = LoggerFactory.getLogger(DoneRetentionJob.class);

  private final DoneRetentionService retention;
  private final DoneRetentionSettingService retentionSetting;
  private final VorhabenArchivierung vorhaben;
  private final Clock clock;

  DoneRetentionJob(
      DoneRetentionService retention,
      DoneRetentionSettingService retentionSetting,
      VorhabenArchivierung vorhaben,
      Clock clock) {
    this.retention = retention;
    this.retentionSetting = retentionSetting;
    this.vorhaben = vorhaben;
    this.clock = clock;
  }

  @Scheduled(cron = "${manban.cleanup.cron:0 0 * * * *}")
  void run() {
    int archived =
        retention.archiveExpiredDoneCards(
            clock.instant(), retentionSetting.effectiveRetentionDays());
    if (archived > 0) {
      log.info("Done-Retention: {} abgelaufene Karten archiviert", archived);
    }
    int archivierteVorhaben = vorhaben.gleicheAlleAb();
    if (archivierteVorhaben > 0) {
      log.info("Vorhaben-Archiv: {} leere Vorhaben archiviert", archivierteVorhaben);
    }
  }
}
