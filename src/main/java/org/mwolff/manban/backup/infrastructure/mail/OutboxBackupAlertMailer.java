package org.mwolff.manban.backup.infrastructure.mail;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.backup.application.BackupAlertMailer;
import org.mwolff.manban.backup.domain.BackupVerdict;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.common.SecureTokens;
import org.mwolff.manban.outbox.application.OutboxMessage;
import org.mwolff.manban.outbox.application.OutboxWriter;
import org.springframework.stereotype.Component;

/**
 * Merkt den Sicherungs-Alarm in der Outbox vor (Issue #828). Zustellung nach Commit über den {@link
 * BackupAlertMailHandler}.
 *
 * <p><strong>Der Idempotenzschlüssel ist der ganze Zustandsspeicher.</strong> Er hängt an (Admin,
 * Beginn des letzten gelungenen Laufs); solange kein neuer gelungener Lauf dazukommt, ergibt jeder
 * stündliche Wachhund-Lauf denselben Schlüssel, und die Outbox legt keinen zweiten Eintrag an. Erst
 * ein neuer Erfolg — der später erneut veraltet — ergibt einen anderen Schlüssel und damit einen
 * zweiten Alarm.
 */
@Component
class OutboxBackupAlertMailer implements BackupAlertMailer {

  /**
   * Die Marke für „noch nie gelungen". Ein leerer Zeitstempel ginge auch, aber eine frische Instanz
   * mit eingeschalteter, nie gelaufener Sicherung ist genau der Fall, den AK10 sichtbar machen soll
   * — er verdient ein Wort und keine Lücke.
   */
  private static final String NIE = "nie";

  private final OutboxWriter outbox;

  OutboxBackupAlertMailer(OutboxWriter outbox) {
    this.outbox = outbox;
  }

  @Override
  public void sendBackupAlert(
      String adminEmail, BackupVerdict failure, @Nullable Instant lastSuccessAt) {
    String marke = lastSuccessAt == null ? NIE : lastSuccessAt.toString();
    outbox.schedule(
        new OutboxMessage(
            BackupAlertMailHandler.TYPE,
            BackupAlertMailHandler.TYPE + ":" + SecureTokens.sha256Hex(adminEmail + "\n" + marke),
            PayloadFields.join(adminEmail, failure.name(), marke)));
  }
}
