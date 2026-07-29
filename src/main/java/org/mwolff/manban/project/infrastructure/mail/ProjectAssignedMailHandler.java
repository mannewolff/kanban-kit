package org.mwolff.manban.project.infrastructure.mail;

import java.util.List;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.outbox.application.OutboxHandler;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.stereotype.Component;

/**
 * Stellt die vom {@link OutboxInvitationMailer} vorgemerkte Zuordnungs-Info zu (Issue #502). Läuft
 * nach dem Commit im Outbox-Worker; ein Versandfehler wird dort als Fehlversuch verbucht und
 * wiederholt.
 */
@Component
class ProjectAssignedMailHandler implements OutboxHandler {

  static final String TYPE = "mail.project-assigned";

  private final JavaMailInvitationMailer delivery;

  ProjectAssignedMailHandler(JavaMailInvitationMailer delivery) {
    this.delivery = delivery;
  }

  @Override
  public String eventType() {
    return TYPE;
  }

  @Override
  public void handle(String payload) {
    List<String> fields = PayloadFields.split(payload, 4);
    delivery.sendProjectAssignedEmail(
        fields.get(0), fields.get(1), ProjectRole.valueOf(fields.get(2)), fields.get(3));
  }
}
