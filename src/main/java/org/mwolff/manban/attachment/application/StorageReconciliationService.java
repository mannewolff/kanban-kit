package org.mwolff.manban.attachment.application;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.mwolff.manban.auth.application.AdminAccessDeniedException;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gleicht Anhang-Metadaten und Objektspeicher ab (Issue #503): findet <em>verwaiste Blobs</em>
 * (Objekt ohne Metadaten — z. B. Altbestand aus Purges vor der Purge-Kaskade oder das Restfenster
 * eines Commit-Abbruchs nach dem Upload-Put) und <em>fehlende Objekte</em> (Metadaten, deren Blob
 * verschwunden ist — der Download endet dort im Speicherfehler).
 *
 * <p><strong>Bewusst nur Bericht, kein automatisches Löschen:</strong> Ein laufender Upload hat
 * zwischen Blob-Put und Commit kurzzeitig ein Objekt ohne committete Metadaten — ein Auto-Cleanup
 * bräuchte Alters-Heuristiken und riskierte echte Daten. Das Aufkommen ist klein; der Betreiber
 * löscht gezielt anhand des Berichts (siehe Betriebs-Doku).
 */
@Service
public class StorageReconciliationService {

  private final AttachmentRepository attachments;
  private final ObjectStorage storage;
  private final PlatformAdminChecker platformAdminChecker;

  public StorageReconciliationService(
      AttachmentRepository attachments,
      ObjectStorage storage,
      PlatformAdminChecker platformAdminChecker) {
    this.attachments = attachments;
    this.storage = storage;
    this.platformAdminChecker = platformAdminChecker;
  }

  /**
   * Erstellt den Abgleich. Nur für Plattform-Admins.
   *
   * @throws AdminAccessDeniedException wenn der Aufrufer kein Plattform-Admin ist (403)
   */
  @Transactional(readOnly = true)
  public ReconciliationReport report(long actorUserId) {
    if (!platformAdminChecker.isPlatformAdmin(actorUserId)) {
      throw new AdminAccessDeniedException();
    }
    Set<String> stored = new HashSet<>(storage.listKeys());
    Set<String> referenced = new HashSet<>(attachments.findAllObjectKeys());
    List<String> orphaned =
        stored.stream().filter(key -> !referenced.contains(key)).sorted().toList();
    List<String> missing =
        referenced.stream().filter(key -> !stored.contains(key)).sorted().toList();
    return new ReconciliationReport(orphaned, missing);
  }

  /**
   * Abgleich-Ergebnis.
   *
   * @param orphanedObjects Object-Keys im Speicher ohne Metadaten-Zeile
   * @param missingObjects Object-Keys aus den Metadaten ohne Objekt im Speicher
   */
  public record ReconciliationReport(List<String> orphanedObjects, List<String> missingObjects) {}
}
