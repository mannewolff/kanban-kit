package org.mwolff.manban.auth.infrastructure.mail;

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

/** Vormerkung der Admin-Freigabe-Benachrichtigung in der Outbox (Issue #502). */
class OutboxAdminNotificationMailerTest {

  private final OutboxWriter outbox = mock(OutboxWriter.class);
  private final OutboxAdminNotificationMailer mailer = new OutboxAdminNotificationMailer(outbox);

  @Test
  void sendNewUserPendingApproval_schedulesEntryWithAllThreeFields() {
    // Given / When
    mailer.sendNewUserPendingApproval("admin@example.org", "neu@example.org", "Neue Nutzerin");

    // Then
    ArgumentCaptor<OutboxMessage> captured = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox).schedule(captured.capture());
    OutboxMessage message = captured.getValue();
    assertThat(message.eventType()).isEqualTo("mail.user-approval");
    assertThat(message.idempotencyKey())
        .isEqualTo(
            "mail.user-approval:" + SecureTokens.sha256Hex("admin@example.org\nneu@example.org"));
    assertThat(PayloadFields.split(message.payload(), 3))
        .containsExactly("admin@example.org", "neu@example.org", "Neue Nutzerin");
  }

  @Test
  void sameAdminAndUser_yieldTheSameKey_soDuplicatesCollapse() {
    // Given / When — der doppelt eingeplante Anlass (z. B. wiederholter Verifikationspfad) muss
    // auf denselben Schlüssel abbilden, damit die Outbox ihn zu einem Eintrag dedupliziert.
    mailer.sendNewUserPendingApproval("admin@example.org", "neu@example.org", "Name A");
    mailer.sendNewUserPendingApproval("admin@example.org", "neu@example.org", "Name B");

    // Then
    ArgumentCaptor<OutboxMessage> captured = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox, times(2)).schedule(captured.capture());
    assertThat(captured.getAllValues().get(0).idempotencyKey())
        .isEqualTo(captured.getAllValues().get(1).idempotencyKey());
  }

  @Test
  void differentAdmins_yieldDifferentKeys_soEachAdminGetsTheMail() {
    // Given / When
    mailer.sendNewUserPendingApproval("admin1@example.org", "neu@example.org", "Name");
    mailer.sendNewUserPendingApproval("admin2@example.org", "neu@example.org", "Name");

    // Then
    ArgumentCaptor<OutboxMessage> captured = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox, times(2)).schedule(captured.capture());
    assertThat(captured.getAllValues().get(0).idempotencyKey())
        .isNotEqualTo(captured.getAllValues().get(1).idempotencyKey());
  }
}
