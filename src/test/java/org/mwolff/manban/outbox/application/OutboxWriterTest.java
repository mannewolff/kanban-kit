package org.mwolff.manban.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.outbox.domain.OutboxEntry;
import org.mwolff.manban.outbox.domain.OutboxStatus;

/** Der Schreibweg legt genau einen offenen Eintrag je fachlichem Ereignis an (Issue #501). */
class OutboxWriterTest {

  private static final Instant NOW = Instant.parse("2026-07-28T10:00:00Z");

  private final OutboxRepository repository = mock(OutboxRepository.class);
  private final OutboxWriter writer =
      new OutboxWriter(repository, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void schedule_writesPendingEntryStampedWithTheClock() {
    // Given
    when(repository.saveIfAbsent(any())).thenReturn(true);

    // When
    writer.schedule(new OutboxMessage("mail.verification", "verify:42", "userId=42"));

    // Then
    ArgumentCaptor<OutboxEntry> captured = ArgumentCaptor.forClass(OutboxEntry.class);
    verify(repository).saveIfAbsent(captured.capture());
    assertThat(captured.getValue())
        .isEqualTo(OutboxEntry.pending("mail.verification", "verify:42", "userId=42", NOW));
    assertThat(captured.getValue().status()).isEqualTo(OutboxStatus.PENDING);
  }

  @Test
  void schedule_reportsThatTheEntryWasNew() {
    // Given
    when(repository.saveIfAbsent(any())).thenReturn(true);

    // When
    boolean scheduled = writer.schedule(new OutboxMessage("mail.verification", "verify:42", "x"));

    // Then
    assertThat(scheduled).isTrue();
  }

  @Test
  void schedule_reportsThatAnEntryWithTheSameKeyAlreadyExisted() {
    // Given
    when(repository.saveIfAbsent(any())).thenReturn(false);

    // When
    boolean scheduled = writer.schedule(new OutboxMessage("mail.verification", "verify:42", "x"));

    // Then
    assertThat(scheduled).isFalse();
  }
}
