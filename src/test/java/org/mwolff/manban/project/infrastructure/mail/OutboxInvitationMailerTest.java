package org.mwolff.manban.project.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.common.SecureTokens;
import org.mwolff.manban.outbox.application.OutboxMessage;
import org.mwolff.manban.outbox.application.OutboxWriter;
import org.mwolff.manban.project.domain.ProjectRole;

/** Vormerkung von Einladungs- und Zuordnungs-Mail in der Outbox (Issue #502). */
class OutboxInvitationMailerTest {

  private static final String INVITATION_URL = "https://app/invitations/accept?token=geheim-789";

  private final OutboxWriter outbox = mock(OutboxWriter.class);
  private final OutboxInvitationMailer mailer = new OutboxInvitationMailer(outbox);

  @Test
  void sendInvitationEmail_schedulesEntryWithHashedKeyAndAllFields() {
    // Given / When
    mailer.sendInvitationEmail("gast@example.org", "Projekt P", INVITATION_URL);

    // Then
    ArgumentCaptor<OutboxMessage> captured = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox).schedule(captured.capture());
    OutboxMessage message = captured.getValue();
    assertThat(message.eventType()).isEqualTo("mail.project-invitation");
    assertThat(message.idempotencyKey())
        .isEqualTo("mail.project-invitation:" + SecureTokens.sha256Hex(INVITATION_URL));
    assertThat(message.idempotencyKey()).doesNotContain("geheim-789");
    assertThat(PayloadFields.split(message.payload(), 3))
        .containsExactly("gast@example.org", "Projekt P", INVITATION_URL);
  }

  @Test
  void sendProjectAssignedEmail_schedulesEntryKeyedByRecipientProjectAndRole() {
    // Given / When
    mailer.sendProjectAssignedEmail(
        "bob@example.org", "Projekt P", ProjectRole.MEMBER, "https://app/projects/9");

    // Then
    ArgumentCaptor<OutboxMessage> captured = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox).schedule(captured.capture());
    OutboxMessage message = captured.getValue();
    assertThat(message.eventType()).isEqualTo("mail.project-assigned");
    assertThat(message.idempotencyKey())
        .isEqualTo(
            "mail.project-assigned:"
                + SecureTokens.sha256Hex("bob@example.org\nhttps://app/projects/9\nMEMBER"));
    assertThat(PayloadFields.split(message.payload(), 4))
        .containsExactly("bob@example.org", "Projekt P", "MEMBER", "https://app/projects/9");
  }

  @Test
  void sendProjectAssignedEmail_differentRoles_yieldDifferentKeys() {
    // Given / When — eine Zuordnung mit neuer Rolle ist ein neuer Anlass und muss zustellen.
    mailer.sendProjectAssignedEmail(
        "bob@example.org", "Projekt P", ProjectRole.MEMBER, "https://app/projects/9");
    mailer.sendProjectAssignedEmail(
        "bob@example.org", "Projekt P", ProjectRole.ADMIN, "https://app/projects/9");

    // Then
    ArgumentCaptor<OutboxMessage> captured = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox, times(2)).schedule(captured.capture());
    assertThat(captured.getAllValues().get(0).idempotencyKey())
        .isNotEqualTo(captured.getAllValues().get(1).idempotencyKey());
  }
}
