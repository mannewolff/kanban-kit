package org.mwolff.manban.attachment.application;

import org.mwolff.manban.attachment.domain.Attachment;
import org.mwolff.manban.card.application.CardsPurgedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Plant beim endgültigen Löschen von Karten die Blob-Löschung ihrer Anhänge ein (Issue #503).
 *
 * <p>Bewusst ein synchroner {@link EventListener}: Er läuft im Transaktions-Scope des Purge und
 * <strong>vor</strong> dessen Delete — nur dann existieren die Metadaten samt {@code object_key}
 * noch. Nach dem Cascade-Delete ({@code card → attachment_meta}) wäre der Schlüssel
 * unwiederbringlich weg und der Blob für immer verwaist. Rollt der Purge zurück, verschwinden die
 * vorgemerkten Aufträge mit (Outbox-Mechanik).
 */
@Component
class AttachmentPurgeListener {

  private final AttachmentRepository attachments;
  private final BlobDeletionScheduler blobDeletion;

  AttachmentPurgeListener(AttachmentRepository attachments, BlobDeletionScheduler blobDeletion) {
    this.attachments = attachments;
    this.blobDeletion = blobDeletion;
  }

  @EventListener
  void onCardsPurged(CardsPurgedEvent event) {
    for (Attachment attachment : attachments.findByCardIds(event.cardIds())) {
      blobDeletion.scheduleDelete(attachment.objectKey());
    }
  }
}
