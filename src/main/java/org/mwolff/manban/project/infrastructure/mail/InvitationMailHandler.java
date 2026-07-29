package org.mwolff.manban.project.infrastructure.mail;

import java.util.List;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.outbox.application.OutboxHandler;
import org.springframework.stereotype.Component;

/**
 * Stellt die vom {@link OutboxInvitationMailer} vorgemerkte Einladungs-E-Mail zu (Issue #502).
 * Läuft nach dem Commit im Outbox-Worker; ein Versandfehler wird dort als Fehlversuch verbucht und
 * wiederholt.
 */
@Component
class InvitationMailHandler implements OutboxHandler {

  static final String TYPE = "mail.project-invitation";

  private final JavaMailInvitationMailer delivery;

  InvitationMailHandler(JavaMailInvitationMailer delivery) {
    this.delivery = delivery;
  }

  @Override
  public String eventType() {
    return TYPE;
  }

  @Override
  public void handle(String payload) {
    List<String> fields = PayloadFields.split(payload, 3);
    delivery.sendInvitationEmail(fields.get(0), fields.get(1), fields.get(2));
  }
}
