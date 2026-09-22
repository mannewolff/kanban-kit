package org.mwolff.manban.kanbancompat.infrastructure;

import java.util.List;
import org.mwolff.manban.common.automation.AutomationStatus;
import org.mwolff.manban.common.automation.AutomationStatusContributor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Meldet die Aufräum-Automatik des kanbancompat-Moduls ans Startprotokoll (Issue #1001).
 *
 * <p>Maßstab für „eingeschaltet" ist wie in den übrigen Modulen die Existenz der Job-Bean, nicht
 * der gebundene Property-Wert (siehe {@code AutomationStatus#eingeschaltet}).
 */
@Component
class KanbancompatAutomationStatusContributor implements AutomationStatusContributor {

  private final ObjectProvider<IdempotencyRetentionJob> retentionJob;

  KanbancompatAutomationStatusContributor(ObjectProvider<IdempotencyRetentionJob> retentionJob) {
    this.retentionJob = retentionJob;
  }

  @Override
  public List<AutomationStatus> statuses() {
    return List.of(
        new AutomationStatus(
            "Aufräumung der Idempotenz-Schlüssel",
            retentionJob.getIfAvailable() != null,
            "MANBAN_CLEANUP_ENABLED",
            "Aufbewahrung %d Stunden".formatted(IdempotencyRetentionJob.RETENTION.toHours())));
  }
}
