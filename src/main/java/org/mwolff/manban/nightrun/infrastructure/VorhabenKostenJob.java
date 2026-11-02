package org.mwolff.manban.nightrun.infrastructure;

import java.time.Clock;
import java.time.Instant;
import org.mwolff.manban.nightrun.application.VorhabenKostenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Der Job, der die Kosten je Vorhaben festhält (Issue #2004, Plan #1990 E8 bis E10; fachlich #1954
 * AK 8): Im Takt {@code manban.nightrun.epic-cost-interval} (Vorgabe fünf Minuten) prüft er jedes
 * Vorhaben jedes Projekts und schreibt nur Übergänge — auch wenn niemand den Leitstand öffnet.
 *
 * <p><b>Je Projekt eine Transaktion.</b> {@link VorhabenKostenService#festhalten} ist ein eigener
 * Bean-Aufruf; scheitert er an einem Projekt, rollt nur dieses zurück, der Fehler wird
 * protokolliert, und die übrigen Projekte laufen weiter. Der nächste Takt versucht es erneut.
 *
 * <p>Abschaltbar wie {@code DoneRetentionJob} über {@code manban.cleanup.enabled=false}. Der erste
 * Takt läuft wie beim Meldejob erst nach einer Taktlänge, nicht schon beim Start der Anwendung.
 */
// PMD.AvoidCatchingGenericException: Der Job muss jeden Fehler an einem Projekt überleben, sonst
// blieben die Vorhaben aller späteren Projekte derselben Liste unbeachtet.
@SuppressWarnings("PMD.AvoidCatchingGenericException")
@Component
@ConditionalOnProperty(name = "manban.cleanup.enabled", havingValue = "true", matchIfMissing = true)
class VorhabenKostenJob {

  private static final Logger log = LoggerFactory.getLogger(VorhabenKostenJob.class);

  private final VorhabenKostenService service;
  private final Clock clock;

  VorhabenKostenJob(VorhabenKostenService service, Clock clock) {
    this.service = service;
    this.clock = clock;
  }

  @Scheduled(
      fixedDelayString = "${manban.nightrun.epic-cost-interval:PT5M}",
      initialDelayString = "${manban.nightrun.epic-cost-interval:PT5M}")
  void run() {
    Instant jetzt = clock.instant();
    for (long projectId : service.projekteMitVorhaben()) {
      try {
        service.festhalten(projectId, jetzt);
      } catch (RuntimeException e) {
        log.warn(
            "Kosten je Vorhaben: Projekt {} nicht festgehalten, nächster Takt versucht es erneut",
            projectId,
            e);
      }
    }
  }
}
