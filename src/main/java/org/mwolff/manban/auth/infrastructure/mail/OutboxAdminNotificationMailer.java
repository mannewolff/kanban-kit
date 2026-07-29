package org.mwolff.manban.auth.infrastructure.mail;

import org.mwolff.manban.auth.application.AdminNotificationMailer;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.common.SecureTokens;
import org.mwolff.manban.outbox.application.OutboxMessage;
import org.mwolff.manban.outbox.application.OutboxWriter;
import org.springframework.stereotype.Component;

/**
 * Merkt die Admin-Benachrichtigung über einen freigabe-wartenden Nutzer in der Outbox vor (Issue
 * #502). Zustellung nach Commit über den {@link AdminNotificationMailHandler}.
 *
 * <p>Der Idempotenzschlüssel hängt an (Admin, neuer Nutzer): Wird dieselbe Benachrichtigung
 * mehrfach eingeplant — etwa durch einen wiederholten Verifikationspfad — entsteht genau ein
 * Eintrag und damit höchstens eine Zustellung je Admin.
 */
@Component
class OutboxAdminNotificationMailer implements AdminNotificationMailer {

  private final OutboxWriter outbox;

  OutboxAdminNotificationMailer(OutboxWriter outbox) {
    this.outbox = outbox;
  }

  @Override
  public void sendNewUserPendingApproval(
      String adminEmail, String newUserEmail, String newUserDisplayName) {
    outbox.schedule(
        new OutboxMessage(
            AdminNotificationMailHandler.TYPE,
            AdminNotificationMailHandler.TYPE
                + ":"
                + SecureTokens.sha256Hex(adminEmail + "\n" + newUserEmail),
            PayloadFields.join(adminEmail, newUserEmail, newUserDisplayName)));
  }
}
