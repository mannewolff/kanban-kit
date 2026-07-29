package org.mwolff.manban.attachment.application;

import org.mwolff.manban.outbox.application.OutboxMessage;
import org.mwolff.manban.outbox.application.OutboxWriter;
import org.springframework.stereotype.Service;

/**
 * Plant das Löschen eines Blobs im Objektspeicher über die Outbox ein (Issue #503).
 *
 * <p><strong>Warum nicht direkt löschen:</strong> Eine Spring-Transaktion kann MinIO nicht
 * zurückrollen. Der Löschauftrag wird deshalb in derselben Transaktion wie die Metadaten-Änderung
 * vorgemerkt und nach dem Commit vom Outbox-Worker ausgeführt — Rollback nimmt den Auftrag mit,
 * Commit garantiert die (notfalls wiederholte) Ausführung. Der schlimmste verbleibende Fall ist ein
 * <em>verwaister Blob</em> (unsichtbar, über die {@link StorageReconciliationService
 * Reconciliation} auffindbar) statt eines <em>kaputten Verweises</em> (sichtbar, störend).
 *
 * <p>Der Idempotenzschlüssel ist der Object-Key selbst (UUID-basiert, dauerhaft eindeutig): Planen
 * Einzel-Löschung und Purge-Kaskade denselben Blob ein, entsteht genau ein Auftrag.
 */
@Service
public class BlobDeletionScheduler {

  /** Ereignistyp; die Payload ist der rohe Object-Key. */
  public static final String TYPE = "attachment.blob-delete";

  private final OutboxWriter outbox;

  public BlobDeletionScheduler(OutboxWriter outbox) {
    this.outbox = outbox;
  }

  /** Merkt die Löschung des Blobs in der laufenden Transaktion vor. */
  public void scheduleDelete(String objectKey) {
    outbox.schedule(new OutboxMessage(TYPE, TYPE + ":" + objectKey, objectKey));
  }
}
