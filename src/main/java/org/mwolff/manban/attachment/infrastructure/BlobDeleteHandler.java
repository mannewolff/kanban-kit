package org.mwolff.manban.attachment.infrastructure;

import org.mwolff.manban.attachment.application.BlobDeletionScheduler;
import org.mwolff.manban.attachment.application.ObjectStorage;
import org.mwolff.manban.outbox.application.OutboxHandler;
import org.springframework.stereotype.Component;

/**
 * Führt den vom {@link BlobDeletionScheduler} vorgemerkten Blob-Löschauftrag aus (Issue #503).
 * Läuft nach dem Commit im Outbox-Worker; ein Speicherfehler wird dort als Fehlversuch verbucht und
 * wiederholt.
 *
 * <p>Die Payload ist der rohe Object-Key. Das Löschen ist idempotent (S3-Semantik: das Entfernen
 * eines nicht existierenden Objekts ist kein Fehler) — ein wiederholter Auftrag oder ein bereits
 * anderweitig entferntes Objekt endet als Erfolg, nicht als ewiger Fehlversuch (per IT belegt).
 */
@Component
class BlobDeleteHandler implements OutboxHandler {

  private final ObjectStorage storage;

  BlobDeleteHandler(ObjectStorage storage) {
    this.storage = storage;
  }

  @Override
  public String eventType() {
    return BlobDeletionScheduler.TYPE;
  }

  @Override
  public void handle(String payload) {
    storage.delete(payload);
  }
}
