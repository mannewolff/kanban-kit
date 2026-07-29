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

/** Vormerkung der Passwort-Reset-Mail in der Outbox (Issue #502). */
class OutboxPasswordResetMailerTest {

  private static final String URL = "https://app/reset?token=geheim-456";

  private final OutboxWriter outbox = mock(OutboxWriter.class);
  private final OutboxPasswordResetMailer mailer = new OutboxPasswordResetMailer(outbox);

  @Test
  void sendPasswordResetEmail_schedulesEntryWithHashedKeyAndBothFields() {
    // Given / When
    mailer.sendPasswordResetEmail("bob@example.org", URL);

    // Then
    ArgumentCaptor<OutboxMessage> captured = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox).schedule(captured.capture());
    OutboxMessage message = captured.getValue();
    assertThat(message.eventType()).isEqualTo("mail.password-reset");
    assertThat(message.idempotencyKey())
        .isEqualTo("mail.password-reset:" + SecureTokens.sha256Hex(URL));
    assertThat(message.idempotencyKey()).doesNotContain("geheim-456");
    assertThat(PayloadFields.split(message.payload(), 2)).containsExactly("bob@example.org", URL);
  }
}
