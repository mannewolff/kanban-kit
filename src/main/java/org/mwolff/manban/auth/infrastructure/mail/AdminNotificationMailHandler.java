package org.mwolff.manban.auth.infrastructure.mail;

import java.util.List;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.outbox.application.OutboxHandler;
import org.springframework.stereotype.Component;

/**
 * Stellt die vom {@link OutboxAdminNotificationMailer} vorgemerkte Freigabe-Benachrichtigung zu
 * (Issue #502). Läuft nach dem Commit im Outbox-Worker; ein Versandfehler wird dort als Fehlversuch
 * verbucht und wiederholt.
 */
@Component
class AdminNotificationMailHandler implements OutboxHandler {

  static final String TYPE = "mail.user-approval";

  private final JavaMailAdminNotificationMailer delivery;

  AdminNotificationMailHandler(JavaMailAdminNotificationMailer delivery) {
    this.delivery = delivery;
  }

  @Override
  public String eventType() {
    return TYPE;
  }

  @Override
  public void handle(String payload) {
    List<String> fields = PayloadFields.split(payload, 3);
    delivery.sendNewUserPendingApproval(fields.get(0), fields.get(1), fields.get(2));
  }
}
