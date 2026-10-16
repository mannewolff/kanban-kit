package org.mwolff.manban.auth.infrastructure.mail;

import java.util.List;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.outbox.application.OutboxHandler;
import org.springframework.stereotype.Component;

/**
 * Stellt die vom {@link OutboxLoginCodeMailer} vorgemerkte Anmeldecode-Mail zu (Plan #1801, A12).
 * Läuft nach dem Commit im Outbox-Worker; ein Versandfehler wird dort als Fehlversuch verbucht und
 * wiederholt.
 */
@Component
class LoginCodeMailHandler implements OutboxHandler {

  static final String TYPE = "mail.login-code";

  private final JavaMailLoginCodeMailer delivery;

  LoginCodeMailHandler(JavaMailLoginCodeMailer delivery) {
    this.delivery = delivery;
  }

  @Override
  public String eventType() {
    return TYPE;
  }

  @Override
  public void handle(String payload) {
    List<String> fields = PayloadFields.split(payload, 3);
    delivery.sendLoginCode(fields.get(0), fields.get(1), fields.get(2));
  }
}
