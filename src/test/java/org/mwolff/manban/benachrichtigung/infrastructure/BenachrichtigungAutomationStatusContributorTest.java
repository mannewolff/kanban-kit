package org.mwolff.manban.benachrichtigung.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungProperties;
import org.mwolff.manban.common.automation.AutomationStatus;

/** Der Beitrag der Benachrichtigungen zum Startprotokoll (Issue #2081, Plan #2070 E10). */
class BenachrichtigungAutomationStatusContributorTest {

  private static final String SCHALTER = "MANBAN_MAIL_ENABLED";

  private static List<AutomationStatus> statuses(boolean mailEnabled) {
    return new BenachrichtigungAutomationStatusContributor(
            new BenachrichtigungProperties(null, Duration.ofSeconds(90), 0), mailEnabled)
        .statuses();
  }

  @Test
  void mitMailversandLaufenJobUndVersand() {
    assertThat(statuses(true))
        .containsExactly(
            new AutomationStatus("Benachrichtigungs-Job", true, SCHALTER, "Takt 90 s"),
            new AutomationStatus("Benachrichtigungs-Versand", true, SCHALTER, "über die Outbox"));
  }

  @Test
  void ohneMailversandErscheinenBeideAlsAbgeschaltet() {
    assertThat(statuses(false))
        .containsExactly(
            new AutomationStatus("Benachrichtigungs-Job", false, SCHALTER, "Takt 90 s"),
            new AutomationStatus("Benachrichtigungs-Versand", false, SCHALTER, "über die Outbox"));
  }
}
