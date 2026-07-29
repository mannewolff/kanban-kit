package org.mwolff.manban.project.infrastructure.mail;

import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.common.SecureTokens;
import org.mwolff.manban.outbox.application.OutboxMessage;
import org.mwolff.manban.outbox.application.OutboxWriter;
import org.mwolff.manban.project.application.InvitationMailer;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.stereotype.Component;

/**
 * Merkt projektbezogene E-Mails (Einladung, Zuordnungs-Info) in der Outbox vor, statt sie synchron
 * zu versenden (Issue #502). Zustellung nach Commit über {@link InvitationMailHandler} bzw. {@link
 * ProjectAssignedMailHandler}.
 *
 * <p>Schlüsselbildung: Die Einladung hängt am Hash der Einladungs-URL (eindeutig je Token, das
 * Geheimnis bleibt draußen). Die Zuordnungs-Info hängt an (Empfänger, Projekt-URL, Rolle) — ein
 * wiederholtes Zuordnen mit derselben Rolle ist fachlich ein No-op und erzeugt innerhalb der
 * Outbox-Aufbewahrungsfrist keine zweite Mail.
 */
@Component
class OutboxInvitationMailer implements InvitationMailer {

  private final OutboxWriter outbox;

  OutboxInvitationMailer(OutboxWriter outbox) {
    this.outbox = outbox;
  }

  @Override
  public void sendInvitationEmail(String toEmail, String projectName, String invitationUrl) {
    outbox.schedule(
        new OutboxMessage(
            InvitationMailHandler.TYPE,
            InvitationMailHandler.TYPE + ":" + SecureTokens.sha256Hex(invitationUrl),
            PayloadFields.join(toEmail, projectName, invitationUrl)));
  }

  @Override
  public void sendProjectAssignedEmail(
      String toEmail, String projectName, ProjectRole role, String projectUrl) {
    outbox.schedule(
        new OutboxMessage(
            ProjectAssignedMailHandler.TYPE,
            ProjectAssignedMailHandler.TYPE
                + ":"
                + SecureTokens.sha256Hex(toEmail + "\n" + projectUrl + "\n" + role.name()),
            PayloadFields.join(toEmail, projectName, role.name(), projectUrl)));
  }
}
