package org.mwolff.manban.card.infrastructure;

import java.util.List;
import org.mwolff.manban.card.application.CleanupProperties;
import org.mwolff.manban.card.application.DoneRetentionSettingService;
import org.mwolff.manban.card.application.DoneRetentionSettingService.RetentionSettings;
import org.mwolff.manban.common.automation.AutomationStatus;
import org.mwolff.manban.common.automation.AutomationStatusContributor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Meldet die beiden Aufräum-Automatiken des card-Moduls ans Startprotokoll (Issue #908).
 *
 * <p><b>Maßstab für „eingeschaltet" ist die Existenz der Job-Bean</b>, nicht der Property-Wert.
 * {@code @ConditionalOnProperty(havingValue = "true")} nimmt nur genau {@code "true"}; der
 * Spring-Binder nähme daneben auch {@code yes}, {@code on} und {@code 1}. Wer den gebundenen Wert
 * meldete, schriebe bei {@code MANBAN_CLEANUP_ENABLED=yes} „eingeschaltet" ins Protokoll, während
 * der Job in Wahrheit fehlt. Deshalb {@link ObjectProvider}: Er fragt die Bean, ohne sie zu
 * erzwingen.
 *
 * <p><b>Die Done-Archivierung ist der schwierige Fall.</b> Ihre Frist kann ein Plattform-Admin zur
 * Laufzeit übersteuern, und ein Override von {@code 0} schaltet die Archivierung ab — die Job-Bean
 * existiert dann weiterhin, sie archiviert nur nichts. Ein Protokoll, das bloß die konfigurierte
 * Frist nennt, meldete „eingeschaltet, Frist 30 Tage" für eine Automatik, die stillsteht.
 *
 * <p>Die Quelle der Frist kommt deshalb aus {@link RetentionSettings#override()} und <b>nicht</b>
 * aus einem Vergleich des effektiven Werts mit der Konfiguration: Setzt ein Admin den Override auf
 * genau den konfigurierten Wert, wären beide Zahlen gleich, und der Vergleich meldete
 * „Konfiguration" für eine Einstellung, die in der Datenbank steht.
 */
@Component
class CleanupAutomationStatusContributor implements AutomationStatusContributor {

  /** Beide Automatiken hängen an diesem einen Schalter — so führt ihn auch die Anleitung. */
  private static final String SCHALTER = "MANBAN_CLEANUP_ENABLED";

  private final ObjectProvider<TrashRetentionJob> trashJob;
  private final ObjectProvider<DoneRetentionJob> doneJob;
  private final CleanupProperties properties;
  private final DoneRetentionSettingService retentionSetting;

  CleanupAutomationStatusContributor(
      ObjectProvider<TrashRetentionJob> trashJob,
      ObjectProvider<DoneRetentionJob> doneJob,
      CleanupProperties properties,
      DoneRetentionSettingService retentionSetting) {
    this.trashJob = trashJob;
    this.doneJob = doneJob;
    this.properties = properties;
    this.retentionSetting = retentionSetting;
  }

  @Override
  public List<AutomationStatus> statuses() {
    return List.of(papierkorb(), doneArchivierung());
  }

  private AutomationStatus papierkorb() {
    return new AutomationStatus(
        "Papierkorb-Löschung",
        trashJob.getIfAvailable() != null,
        SCHALTER,
        "Frist %d Tage".formatted(properties.trashRetentionDays()));
  }

  private AutomationStatus doneArchivierung() {
    RetentionSettings frist = retentionSetting.retentionForStartup();
    String quelle = frist.override() != null ? "Plattform-Admin-Override" : "Konfiguration";
    // Ein Override von 0 schaltet die Archivierung ab, obwohl die Bean steht (Semantik von
    // DoneRetentionSettingService). Beides muss zutreffen, damit hier "eingeschaltet" steht.
    boolean laeuft = doneJob.getIfAvailable() != null && frist.effective() > 0;
    return new AutomationStatus(
        "Done-Archivierung",
        laeuft,
        SCHALTER,
        "Frist %d Tage (%s)".formatted(frist.effective(), quelle));
  }
}
