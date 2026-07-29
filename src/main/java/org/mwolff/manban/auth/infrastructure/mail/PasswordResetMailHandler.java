package org.mwolff.manban.auth.infrastructure.mail;

import java.util.List;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.outbox.application.OutboxHandler;
import org.springframework.stereotype.Component;

/**
 * Stellt die vom {@link OutboxPasswordResetMailer} vorgemerkte Reset-E-Mail zu (Issue #502). Läuft
 * nach dem Commit im Outbox-Worker; ein Versandfehler wird dort als Fehlversuch verbucht und
 * wiederholt.
 */
@Component
class PasswordResetMailHandler implements OutboxHandler {

  static final String TYPE = "mail.password-reset";

  private final JavaMailPasswordResetMailer delivery;

  PasswordResetMailHandler(JavaMailPasswordResetMailer delivery) {
    this.delivery = delivery;
  }

  @Override
  public String eventType() {
    return TYPE;
  }

  @Override
  public void handle(String payload) {
    List<String> fields = PayloadFields.split(payload, 2);
    delivery.sendPasswordResetEmail(fields.get(0), fields.get(1));
  }
}
