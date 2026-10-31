package org.mwolff.manban.push.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.automation.AutomationStatus;
import org.mwolff.manban.push.application.PushProperties;

/**
 * Der Beitrag des push-Moduls zum Startprotokoll (Issue #1998, Muster der Outbox): Meldejob und
 * Versand erscheinen immer, eingeschaltet nur mit vollständigen VAPID-Angaben (Plan #1989 E16).
 */
class PushAutomationStatusContributorTest {

  private static final String SCHALTER =
      "MANBAN_PUSH_VAPID_PUBLIC_KEY, MANBAN_PUSH_VAPID_PRIVATE_KEY, MANBAN_PUSH_SUBJECT";

  private static List<AutomationStatus> statuses(PushProperties properties) {
    return new PushAutomationStatusContributor(properties).statuses();
  }

  @Test
  void mitSchluesselnLaufenMeldejobUndVersand() {
    // Der Takt weicht bewusst von der Vorgabe ab: So stammt der Text sichtbar aus den Properties.
    List<AutomationStatus> stati =
        statuses(new PushProperties("pub", "priv", "mailto:a@b.de", Duration.ofSeconds(90)));

    assertThat(stati)
        .containsExactly(
            new AutomationStatus("Push-Meldejob", true, SCHALTER, "Takt 90 s"),
            new AutomationStatus(
                "Push-Versand", true, SCHALTER, "über die Outbox, Aufbewahrung 12 Stunden"));
  }

  @Test
  void ohneSchluesselErscheinenBeideAlsAbgeschaltet() {
    List<AutomationStatus> stati = statuses(new PushProperties(null, null, null, null));

    assertThat(stati).hasSize(2).noneMatch(AutomationStatus::eingeschaltet);
    assertThat(stati)
        .extracting(AutomationStatus::bezeichnung)
        .containsExactly("Push-Meldejob", "Push-Versand");
  }
}
