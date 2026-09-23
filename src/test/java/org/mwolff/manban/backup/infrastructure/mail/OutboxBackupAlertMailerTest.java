package org.mwolff.manban.backup.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.backup.domain.BackupVerdict;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.common.SecureTokens;
import org.mwolff.manban.outbox.application.OutboxMessage;
import org.mwolff.manban.outbox.application.OutboxWriter;

/**
 * Vormerkung des Sicherungs-Alarms in der Outbox (Issue #828).
 *
 * <p>Der Idempotenzschlüssel ist hier der ganze Zustandsspeicher: Solange kein neuer gelungener
 * Lauf dazukommt, ergibt jeder Wachhund-Lauf denselben Schlüssel, und die Outbox unterdrückt die
 * Wiederholung. Deshalb wird er hier Zeichen für Zeichen festgeschrieben.
 */
class OutboxBackupAlertMailerTest {

  private static final String TYPE = "mail.backup-alert";
  private static final Instant ERFOLG = Instant.parse("2026-09-20T03:00:00Z");

  private final OutboxWriter outbox = mock(OutboxWriter.class);
  private final OutboxBackupAlertMailer mailer = new OutboxBackupAlertMailer(outbox);

  private OutboxMessage eingeplant(int erwarteteAufrufe) {
    ArgumentCaptor<OutboxMessage> captured = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox, times(erwarteteAufrufe)).schedule(captured.capture());
    return captured.getValue();
  }

  @Test
  void planteinenEintragMitAllenDreiFeldern() {
    // Given / When
    mailer.sendBackupAlert("admin@example.org", BackupVerdict.VERALTET, ERFOLG);

    // Then
    OutboxMessage message = eingeplant(1);
    assertThat(message.eventType()).isEqualTo(TYPE);
    assertThat(message.idempotencyKey())
        .isEqualTo(TYPE + ":" + SecureTokens.sha256Hex("admin@example.org\n2026-09-20T03:00:00Z"));
    assertThat(PayloadFields.split(message.payload(), 3))
        .containsExactly("admin@example.org", "VERALTET", "2026-09-20T03:00:00Z");
  }

  @Test
  void ohneJemalsGelungenenLaufTraegtDerSchluesselDieMarkeNie() {
    // Given / When
    mailer.sendBackupAlert("admin@example.org", BackupVerdict.VERALTET, null);

    // Then
    OutboxMessage message = eingeplant(1);
    assertThat(message.idempotencyKey())
        .isEqualTo(TYPE + ":" + SecureTokens.sha256Hex("admin@example.org\nnie"));
    assertThat(PayloadFields.split(message.payload(), 3))
        .containsExactly("admin@example.org", "VERALTET", "nie");
  }

  @Test
  void gleicherAdminUndGleicherErfolgErgebenDenselbenSchluessel() {
    // Given / When — zwei aufeinanderfolgende Wachhund-Läufe ohne neuen Erfolg dazwischen.
    mailer.sendBackupAlert("admin@example.org", BackupVerdict.VERALTET, ERFOLG);
    mailer.sendBackupAlert("admin@example.org", BackupVerdict.VERALTET, ERFOLG);

    // Then
    ArgumentCaptor<OutboxMessage> captured = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox, times(2)).schedule(captured.capture());
    assertThat(captured.getAllValues().get(0).idempotencyKey())
        .isEqualTo(captured.getAllValues().get(1).idempotencyKey());
  }

  @Test
  void einNeuerErfolgErgibtEinenAnderenSchluessel() {
    // Given / When
    mailer.sendBackupAlert("admin@example.org", BackupVerdict.VERALTET, ERFOLG);
    mailer.sendBackupAlert("admin@example.org", BackupVerdict.VERALTET, ERFOLG.plusSeconds(1));

    // Then
    ArgumentCaptor<OutboxMessage> captured = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox, times(2)).schedule(captured.capture());
    assertThat(captured.getAllValues().get(0).idempotencyKey())
        .isNotEqualTo(captured.getAllValues().get(1).idempotencyKey());
  }

  @Test
  void verschiedeneAdminsErgebenVerschiedeneSchluessel() {
    // Given / When
    mailer.sendBackupAlert("a@example.org", BackupVerdict.VERALTET, ERFOLG);
    mailer.sendBackupAlert("b@example.org", BackupVerdict.VERALTET, ERFOLG);

    // Then
    ArgumentCaptor<OutboxMessage> captured = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox, times(2)).schedule(captured.capture());
    assertThat(captured.getAllValues().get(0).idempotencyKey())
        .isNotEqualTo(captured.getAllValues().get(1).idempotencyKey());
  }
}
