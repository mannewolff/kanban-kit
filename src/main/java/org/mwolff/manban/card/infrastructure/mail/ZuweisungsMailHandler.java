package org.mwolff.manban.card.infrastructure.mail;

import java.util.List;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.outbox.application.OutboxHandler;
import org.springframework.stereotype.Component;

/**
 * Stellt die vom {@link OutboxZuweisungsMailer} vorgemerkte Nachricht an neu Zuständige zu (Issue
 * #1916). Läuft nach dem Commit im Outbox-Worker; ein Versandfehler wird dort als Fehlversuch
 * verbucht und wiederholt.
 */
@Component
class ZuweisungsMailHandler implements OutboxHandler {

  static final String TYPE = "mail.card-assigned";

  private final JavaMailZuweisungsMailer delivery;

  ZuweisungsMailHandler(JavaMailZuweisungsMailer delivery) {
    this.delivery = delivery;
  }

  @Override
  public String eventType() {
    return TYPE;
  }

  @Override
  public void handle(String payload) {
    List<String> f = PayloadFields.split(payload, 7);
    delivery.sendZuweisungsEmail(
        new JavaMailZuweisungsMailer.Zuweisung(
            f.get(0), f.get(1), f.get(2), f.get(3), f.get(4), f.get(5), f.get(6)));
  }
}
