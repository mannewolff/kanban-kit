package org.mwolff.manban.auth.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.PayloadFields;

/** Zustellung der vorgemerkten Anmeldecode-Mail (Issue #1804, Plan #1801 A12). */
class LoginCodeMailHandlerTest {

  private final JavaMailLoginCodeMailer delivery = mock(JavaMailLoginCodeMailer.class);
  private final LoginCodeMailHandler handler = new LoginCodeMailHandler(delivery);

  @Test
  void eventType_matchesTheSchedulerType() {
    assertThat(handler.eventType()).isEqualTo("mail.login-code");
  }

  @Test
  void handle_deliversTheDecodedFields() {
    // Given / When
    handler.handle(
        PayloadFields.join("bob@example.org", "004711", "https://app/login/link?token=abc"));

    // Then
    verify(delivery).sendLoginCode("bob@example.org", "004711", "https://app/login/link?token=abc");
  }

  @Test
  void handle_rejectsMalformedPayloadWithoutDelivering() {
    // Given / When / Then
    assertThatThrownBy(() -> handler.handle(PayloadFields.join("a", "b")))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(delivery);
  }
}
