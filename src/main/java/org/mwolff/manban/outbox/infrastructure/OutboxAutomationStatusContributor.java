package org.mwolff.manban.outbox.infrastructure;

import java.util.List;
import org.mwolff.manban.common.automation.AutomationStatus;
import org.mwolff.manban.common.automation.AutomationStatusContributor;
import org.mwolff.manban.outbox.application.OutboxProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Meldet die beiden Automatiken des outbox-Moduls ans Startprotokoll (Issue #909).
 *
 * <p>Wie im card-Modul ist der Maßstab für „eingeschaltet" die Existenz der Bean und nicht der
 * gebundene Property-Wert: {@code @ConditionalOnProperty(havingValue = "true")} nimmt nur genau
 * {@code "true"}, der Spring-Binder daneben auch {@code yes}, {@code on} und {@code 1}.
 *
 * <p><b>Die beiden hängen an verschiedenen Schaltern</b>, und das macht dieses Protokoll erstmals
 * sichtbar: Der Worker folgt {@code MANBAN_OUTBOX_ENABLED}, die Aufräumung dagegen {@code
 * MANBAN_CLEANUP_ENABLED} — sie räumt auf, sie stellt nichts zu. Die Anleitung nennt bei diesem
 * Schalter bisher nur Done-Archivierung und Papierkorb-Leerung; die dritte Wirkung stand nirgends.
 */
@Component
class OutboxAutomationStatusContributor implements AutomationStatusContributor {

  private final ObjectProvider<OutboxWorker> worker;
  private final ObjectProvider<OutboxRetentionJob> retentionJob;
  private final OutboxProperties properties;

  OutboxAutomationStatusContributor(
      ObjectProvider<OutboxWorker> worker,
      ObjectProvider<OutboxRetentionJob> retentionJob,
      OutboxProperties properties) {
    this.worker = worker;
    this.retentionJob = retentionJob;
    this.properties = properties;
  }

  @Override
  public List<AutomationStatus> statuses() {
    return List.of(zustellung(), aufraeumung());
  }

  private AutomationStatus zustellung() {
    return new AutomationStatus(
        "Outbox-Worker",
        worker.getIfAvailable() != null,
        "MANBAN_OUTBOX_ENABLED",
        "Abstand %d ms, höchstens %d Versuche"
            .formatted(properties.pollIntervalMs(), properties.maxAttempts()));
  }

  private AutomationStatus aufraeumung() {
    return new AutomationStatus(
        "Outbox-Aufräumung",
        retentionJob.getIfAvailable() != null,
        // Bewusst der Aufräum-Schalter und nicht MANBAN_OUTBOX_ENABLED: OutboxRetentionJob trägt
        // die Bedingung manban.cleanup.enabled. Kein Tippfehler — wer das "korrigiert", entkoppelt
        // die Anzeige von der Bean, die sie beschreibt.
        "MANBAN_CLEANUP_ENABLED",
        "Aufbewahrung %d Tage".formatted(properties.completedRetentionDays()));
  }
}
