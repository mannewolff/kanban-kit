package org.mwolff.manban.auth.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.common.SecureTokens;
import org.mwolff.manban.outbox.application.OutboxMessage;
import org.mwolff.manban.outbox.application.OutboxWriter;

/** Vormerkung der Verifikations-Mail in der Outbox (Issue #502). */
class OutboxVerificationMailerTest {

  private static final String URL = "https://app/verify?token=geheim-123";

  private final OutboxWriter outbox = mock(OutboxWriter.class);
  private final OutboxVerificationMailer mailer = new OutboxVerificationMailer(outbox);

  @Test
  void sendVerificationEmail_schedulesEntryWithHashedKeyAndBothFields() {
    // Given / When
    mailer.sendVerificationEmail("alice@example.org", URL);

    // Then
    ArgumentCaptor<OutboxMessage> captured = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox).schedule(captured.capture());
    OutboxMessage message = captured.getValue();
    assertThat(message.eventType()).isEqualTo("mail.verification");
    assertThat(message.idempotencyKey())
        .isEqualTo("mail.verification:" + SecureTokens.sha256Hex(URL));
    assertThat(PayloadFields.split(message.payload(), 2)).containsExactly("alice@example.org", URL);
  }

  @Test
  void sendVerificationEmail_keepsTheSecretTokenOutOfTheKey() {
    // Given / When — der Schlüssel ist dauerhaft sichtbar (auch nach dem Payload-Leeren) und darf
    // das geheime Token deshalb nicht tragen.
    mailer.sendVerificationEmail("alice@example.org", URL);

    // Then
    ArgumentCaptor<OutboxMessage> captured = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox).schedule(captured.capture());
    assertThat(captured.getValue().idempotencyKey()).doesNotContain("geheim-123");
  }
}
