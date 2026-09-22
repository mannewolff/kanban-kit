package org.mwolff.manban.ratelimit.infrastructure;

import java.util.List;
import org.mwolff.manban.common.automation.AutomationStatus;
import org.mwolff.manban.common.automation.AutomationStatusContributor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Meldet die Aufräum-Automatik des ratelimit-Moduls ans Startprotokoll (Issue #1000).
 *
 * <p>Maßstab für „eingeschaltet" ist wie in den übrigen Modulen die Existenz der Job-Bean, nicht
 * der gebundene Property-Wert (siehe {@code AutomationStatus#eingeschaltet}).
 */
@Component
class RatelimitAutomationStatusContributor implements AutomationStatusContributor {

  private final ObjectProvider<RejectionRetentionJob> retentionJob;

  RatelimitAutomationStatusContributor(ObjectProvider<RejectionRetentionJob> retentionJob) {
    this.retentionJob = retentionJob;
  }

  @Override
  public List<AutomationStatus> statuses() {
    return List.of(
        new AutomationStatus(
            "Aufräumung der Überlast-Abweisungen",
            retentionJob.getIfAvailable() != null,
            "MANBAN_CLEANUP_ENABLED",
            "Aufbewahrung %d Tage".formatted(RejectionRetentionJob.RETENTION.toDays())));
  }
}
