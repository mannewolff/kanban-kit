package org.mwolff.manban.auth.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.PayloadFields;

/** Zustellung der vorgemerkten Verifikations-Mail (Issue #502). */
class VerificationMailHandlerTest {

  private final JavaMailVerificationMailer delivery = mock(JavaMailVerificationMailer.class);
  private final VerificationMailHandler handler = new VerificationMailHandler(delivery);

  @Test
  void eventType_matchesTheSchedulerType() {
    assertThat(handler.eventType()).isEqualTo("mail.verification");
  }

  @Test
  void handle_deliversTheDecodedFields() {
    // Given / When
    handler.handle(PayloadFields.join("alice@example.org", "https://app/verify?token=abc"));

    // Then
    verify(delivery).sendVerificationEmail("alice@example.org", "https://app/verify?token=abc");
  }

  @Test
  void handle_rejectsMalformedPayloadWithoutDelivering() {
    // Given / When / Then — kaputte Payload endet als Fehlversuch, nicht als Falsch-Zustellung.
    assertThatThrownBy(() -> handler.handle(PayloadFields.join("nur-ein-feld")))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(delivery);
  }
}
