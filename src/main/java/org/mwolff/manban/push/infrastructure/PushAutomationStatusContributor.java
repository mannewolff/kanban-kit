package org.mwolff.manban.push.infrastructure;

import java.util.List;
import org.mwolff.manban.common.automation.AutomationStatus;
import org.mwolff.manban.common.automation.AutomationStatusContributor;
import org.mwolff.manban.push.application.PushProperties;
import org.springframework.stereotype.Component;

/**
 * Meldet die beiden Automatiken des push-Moduls ans Startprotokoll (Issue #1998, Muster der
 * Outbox): den Meldejob und den Versand.
 *
 * <p><b>Maßstab ist {@link PushProperties#aktiv()}</b> und nicht die Existenz einer Bean wie bei
 * der Outbox: Meldejob und Handler bestehen immer und arbeiten nur mit vollständigen VAPID-Angaben
 * (Plan #1989 E16). Halbe Angaben lässt {@link PushSchluesselStartupCheck} gar nicht erst starten,
 * darum gibt es hier nur an oder aus.
 *
 * <p>Der Versand läuft über die Outbox und steht darum zusätzlich unter deren Schalter {@code
 * MANBAN_OUTBOX_ENABLED}; die Zeile des Outbox-Workers steht im selben Protokoll.
 */
@Component
class PushAutomationStatusContributor implements AutomationStatusContributor {

  private static final String SCHALTER =
      "MANBAN_PUSH_VAPID_PUBLIC_KEY, MANBAN_PUSH_VAPID_PRIVATE_KEY, MANBAN_PUSH_SUBJECT";

  private final PushProperties properties;

  PushAutomationStatusContributor(PushProperties properties) {
    this.properties = properties;
  }

  @Override
  public List<AutomationStatus> statuses() {
    boolean an = properties.aktiv();
    return List.of(
        new AutomationStatus(
            "Push-Meldejob",
            an,
            SCHALTER,
            "Takt %d s".formatted(properties.meldeTakt().toSeconds())),
        new AutomationStatus(
            "Push-Versand",
            an,
            SCHALTER,
            "über die Outbox, Aufbewahrung %d Stunden"
                .formatted(LaufNachrichtHandler.AUFBEWAHRUNG.toHours())));
  }
}
