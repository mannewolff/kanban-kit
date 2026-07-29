package org.mwolff.manban.project.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.PayloadFields;

/** Zustellung der vorgemerkten Einladungs-Mail (Issue #502). */
class InvitationMailHandlerTest {

  private final JavaMailInvitationMailer delivery = mock(JavaMailInvitationMailer.class);
  private final InvitationMailHandler handler = new InvitationMailHandler(delivery);

  @Test
  void eventType_matchesTheSchedulerType() {
    assertThat(handler.eventType()).isEqualTo("mail.project-invitation");
  }

  @Test
  void handle_deliversTheDecodedFields() {
    // Given / When
    handler.handle(PayloadFields.join("gast@example.org", "Projekt P", "https://app/accept?t=1"));

    // Then
    verify(delivery).sendInvitationEmail("gast@example.org", "Projekt P", "https://app/accept?t=1");
  }

  @Test
  void handle_rejectsMalformedPayloadWithoutDelivering() {
    // Given / When / Then
    assertThatThrownBy(() -> handler.handle(PayloadFields.join("a", "b")))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(delivery);
  }
}
