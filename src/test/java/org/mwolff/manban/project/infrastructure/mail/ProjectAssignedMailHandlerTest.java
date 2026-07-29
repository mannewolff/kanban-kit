package org.mwolff.manban.project.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.project.domain.ProjectRole;

/** Zustellung der vorgemerkten Zuordnungs-Info (Issue #502). */
class ProjectAssignedMailHandlerTest {

  private final JavaMailInvitationMailer delivery = mock(JavaMailInvitationMailer.class);
  private final ProjectAssignedMailHandler handler = new ProjectAssignedMailHandler(delivery);

  @Test
  void eventType_matchesTheSchedulerType() {
    assertThat(handler.eventType()).isEqualTo("mail.project-assigned");
  }

  @Test
  void handle_deliversTheDecodedFieldsIncludingTheRole() {
    // Given / When
    handler.handle(
        PayloadFields.join("bob@example.org", "Projekt P", "ADMIN", "https://app/projects/9"));

    // Then
    verify(delivery)
        .sendProjectAssignedEmail(
            "bob@example.org", "Projekt P", ProjectRole.ADMIN, "https://app/projects/9");
  }

  @Test
  void handle_rejectsUnknownRoleWithoutDelivering() {
    // Given / When / Then — eine unbekannte Rolle ist eine kaputte Payload, kein Zustellversuch.
    assertThatThrownBy(
            () ->
                handler.handle(
                    PayloadFields.join("bob@example.org", "P", "KAISER", "https://app/p/9")))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(delivery);
  }

  @Test
  void handle_rejectsMalformedPayloadWithoutDelivering() {
    // Given / When / Then
    assertThatThrownBy(() -> handler.handle(PayloadFields.join("a", "b", "c")))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(delivery);
  }
}
