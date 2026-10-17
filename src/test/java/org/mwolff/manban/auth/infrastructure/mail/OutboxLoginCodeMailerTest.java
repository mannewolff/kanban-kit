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

/** Vormerkung der Anmeldecode-Mail in der Outbox (Issue #1804, Plan #1801 A12). */
class OutboxLoginCodeMailerTest {

  private static final String URL = "https://app/login/link?token=geheim-789";

  private final OutboxWriter outbox = mock(OutboxWriter.class);
  private final OutboxLoginCodeMailer mailer = new OutboxLoginCodeMailer(outbox);

  @Test
  void sendLoginCode_schedulesEntryWithHashedKeyAndAllFields() {
    // Given / When
    mailer.sendLoginCode("bob@example.org", "004711", URL);

    // Then
    ArgumentCaptor<OutboxMessage> captured = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox).schedule(captured.capture());
    OutboxMessage message = captured.getValue();
    assertThat(message.eventType()).isEqualTo("mail.login-code");
    assertThat(message.idempotencyKey())
        .isEqualTo("mail.login-code:" + SecureTokens.sha256Hex(URL))
        .doesNotContain("geheim-789")
        .doesNotContain("004711");
    assertThat(PayloadFields.split(message.payload(), 3))
        .containsExactly("bob@example.org", "004711", URL);
  }
}
