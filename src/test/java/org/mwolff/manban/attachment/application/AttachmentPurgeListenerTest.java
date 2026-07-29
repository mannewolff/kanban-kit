package org.mwolff.manban.attachment.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.attachment.domain.Attachment;
import org.mwolff.manban.card.application.CardsPurgedEvent;

/** Blob-Löschung beim endgültigen Löschen von Karten (Issue #503). */
class AttachmentPurgeListenerTest {

  private static final Instant NOW = Instant.parse("2026-07-29T08:00:00Z");

  private final AttachmentRepository attachments = mock(AttachmentRepository.class);
  private final BlobDeletionScheduler blobDeletion = mock(BlobDeletionScheduler.class);
  private final AttachmentPurgeListener listener =
      new AttachmentPurgeListener(attachments, blobDeletion);

  private static Attachment attachment(long id, long cardId, String objectKey) {
    return new Attachment(id, cardId, "f.bin", "application/octet-stream", 1, objectKey, NOW);
  }

  @Test
  void onCardsPurged_schedulesBlobDeletionForEveryAttachment() {
    // Given
    when(attachments.findByCardIds(List.of(5L, 6L)))
        .thenReturn(List.of(attachment(1L, 5L, "cards/5/a"), attachment(2L, 6L, "cards/6/b")));

    // When
    listener.onCardsPurged(new CardsPurgedEvent(List.of(5L, 6L)));

    // Then
    verify(blobDeletion).scheduleDelete("cards/5/a");
    verify(blobDeletion).scheduleDelete("cards/6/b");
  }

  @Test
  void onCardsPurged_schedulesNothing_whenCardsHaveNoAttachments() {
    // Given
    when(attachments.findByCardIds(List.of(5L))).thenReturn(List.of());

    // When
    listener.onCardsPurged(new CardsPurgedEvent(List.of(5L)));

    // Then
    verifyNoInteractions(blobDeletion);
  }
}
