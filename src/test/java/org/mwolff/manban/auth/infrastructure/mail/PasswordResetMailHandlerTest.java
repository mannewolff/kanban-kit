package org.mwolff.manban.auth.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.PayloadFields;

/** Zustellung der vorgemerkten Passwort-Reset-Mail (Issue #502). */
class PasswordResetMailHandlerTest {

  private final JavaMailPasswordResetMailer delivery = mock(JavaMailPasswordResetMailer.class);
  private final PasswordResetMailHandler handler = new PasswordResetMailHandler(delivery);

  @Test
  void eventType_matchesTheSchedulerType() {
    assertThat(handler.eventType()).isEqualTo("mail.password-reset");
  }

  @Test
  void handle_deliversTheDecodedFields() {
    // Given / When
    handler.handle(PayloadFields.join("bob@example.org", "https://app/reset?token=abc"));

    // Then
    verify(delivery).sendPasswordResetEmail("bob@example.org", "https://app/reset?token=abc");
  }

  @Test
  void handle_rejectsMalformedPayloadWithoutDelivering() {
    // Given / When / Then
    assertThatThrownBy(() -> handler.handle(PayloadFields.join("a", "b", "c")))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(delivery);
  }
}
