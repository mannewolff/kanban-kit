package org.mwolff.manban.auth.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.PayloadFields;

/** Zustellung der vorgemerkten Admin-Freigabe-Benachrichtigung (Issue #502). */
class AdminNotificationMailHandlerTest {

  private final JavaMailAdminNotificationMailer delivery =
      mock(JavaMailAdminNotificationMailer.class);
  private final AdminNotificationMailHandler handler = new AdminNotificationMailHandler(delivery);

  @Test
  void eventType_matchesTheSchedulerType() {
    assertThat(handler.eventType()).isEqualTo("mail.user-approval");
  }

  @Test
  void handle_deliversTheDecodedFields() {
    // Given / When
    handler.handle(PayloadFields.join("admin@example.org", "neu@example.org", "Neue Nutzerin"));

    // Then
    verify(delivery)
        .sendNewUserPendingApproval("admin@example.org", "neu@example.org", "Neue Nutzerin");
  }

  @Test
  void handle_rejectsMalformedPayloadWithoutDelivering() {
    // Given / When / Then
    assertThatThrownBy(() -> handler.handle(PayloadFields.join("a", "b")))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(delivery);
  }
}
