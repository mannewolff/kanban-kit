package org.mwolff.manban.benachrichtigung.infrastructure;

import java.util.List;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungProperties;
import org.mwolff.manban.common.automation.AutomationStatus;
import org.mwolff.manban.common.automation.AutomationStatusContributor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Meldet Job und Versand der Benachrichtigungen ans Startprotokoll (Issue #2081, Muster von Push).
 *
 * <p>Der Versand läuft über die Outbox und steht darum zusätzlich unter deren Schalter {@code
 * MANBAN_OUTBOX_ENABLED}; die Zeile des Outbox-Workers steht im selben Protokoll.
 */
@Component
class BenachrichtigungAutomationStatusContributor implements AutomationStatusContributor {

  private static final String SCHALTER = "MANBAN_MAIL_ENABLED";

  private final BenachrichtigungProperties properties;
  private final boolean mailEnabled;

  BenachrichtigungAutomationStatusContributor(
      BenachrichtigungProperties properties,
      @Value("${manban.mail.enabled:false}") boolean mailEnabled) {
    this.properties = properties;
    this.mailEnabled = mailEnabled;
  }

  @Override
  public List<AutomationStatus> statuses() {
    return List.of(
        new AutomationStatus(
            "Benachrichtigungs-Job",
            mailEnabled,
            SCHALTER,
            "Takt %d s".formatted(properties.takt().toSeconds())),
        new AutomationStatus(
            "Benachrichtigungs-Versand", mailEnabled, SCHALTER, "über die Outbox"));
  }
}
