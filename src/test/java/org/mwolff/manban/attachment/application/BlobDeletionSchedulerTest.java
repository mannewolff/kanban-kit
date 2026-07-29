package org.mwolff.manban.attachment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.outbox.application.OutboxMessage;
import org.mwolff.manban.outbox.application.OutboxWriter;

/** Vormerkung der Blob-Löschung in der Outbox (Issue #503). */
class BlobDeletionSchedulerTest {

  private final OutboxWriter outbox = mock(OutboxWriter.class);
  private final BlobDeletionScheduler scheduler = new BlobDeletionScheduler(outbox);

  @Test
  void scheduleDelete_schedulesEntryKeyedByObjectKey() {
    // Given / When
    scheduler.scheduleDelete("cards/5/abc-123");

    // Then
    ArgumentCaptor<OutboxMessage> captured = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox).schedule(captured.capture());
    OutboxMessage message = captured.getValue();
    assertThat(message.eventType()).isEqualTo("attachment.blob-delete");
    assertThat(message.idempotencyKey()).isEqualTo("attachment.blob-delete:cards/5/abc-123");
    assertThat(message.payload()).isEqualTo("cards/5/abc-123");
  }

  @Test
  void sameObjectKey_yieldsTheSameKey_soDuplicateOrdersCollapse() {
    // Given / When — Einzel-Löschung und Purge-Kaskade könnten denselben Blob einplanen.
    scheduler.scheduleDelete("cards/5/abc-123");
    scheduler.scheduleDelete("cards/5/abc-123");

    // Then
    ArgumentCaptor<OutboxMessage> captured = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox, times(2)).schedule(captured.capture());
    assertThat(captured.getAllValues().get(0).idempotencyKey())
        .isEqualTo(captured.getAllValues().get(1).idempotencyKey());
  }
}
