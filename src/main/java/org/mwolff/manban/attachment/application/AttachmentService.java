package org.mwolff.manban.attachment.application;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.mwolff.manban.attachment.domain.Attachment;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anhang-Use-Cases. Der Blob wandert in den Objektspeicher (MinIO), nur Metadaten in die DB.
 * Content-Type wird aus den Magic-Bytes bestimmt (nicht dem Client vertraut). Upload erfordert
 * ATTACHMENT_CREATE, Löschen ATTACHMENT_DELETE, Lesen nur Mitgliedschaft.
 *
 * <p><b>Rechteprüfung ist projekt-basiert, nicht board-basiert</b> (Entscheidung aus #462). Die
 * Projekt-ID kommt über {@link CardService#requireProjectId(long)} direkt von der Karte. Daraus
 * folgt bewusst zweierlei — beides ist in {@code AttachmentIT} festgenagelt und darf beim nächsten
 * Refactor nicht stillschweigend kippen:
 *
 * <ul>
 *   <li>Anhänge an Karten eines <b>archivierten Boards</b> bleiben nutzbar. Das Archivieren
 *       entzieht das Board-Aggregat (→ 404), nicht dessen Karten: die bleiben editierbar und tragen
 *       weiterhin Kommentare. Anhänge folgen dieser Linie.
 *   <li>Anhänge an <b>board-lose Pool-Ideen</b> (#405) funktionieren; sie haben kein Board, über
 *       das sich eine Projekt-ID auflösen ließe.
 * </ul>
 *
 * <p>Die frühere Auflösung über das Board (gelöschtes {@code projectIdOfCard}) lieferte in beiden
 * Fällen 404 bzw. 500 und machte Anhänge zum einzigen Ausreißer gegenüber {@code CommentService},
 * der schon immer projekt-basiert prüft.
 */
@Service
public class AttachmentService {

  private final AttachmentRepository attachments;
  private final ObjectStorage storage;
  private final ContentTypeDetector contentTypeDetector;
  private final ObjectStorageProperties properties;
  private final CardService cardService;
  private final PermissionChecker permissions;
  private final BlobDeletionScheduler blobDeletion;
  private final Clock clock;

  public AttachmentService(
      AttachmentRepository attachments,
      ObjectStorage storage,
      ContentTypeDetector contentTypeDetector,
      ObjectStorageProperties properties,
      CardService cardService,
      PermissionChecker permissions,
      BlobDeletionScheduler blobDeletion,
      Clock clock) {
    this.attachments = attachments;
    this.storage = storage;
    this.contentTypeDetector = contentTypeDetector;
    this.properties = properties;
    this.cardService = cardService;
    this.permissions = permissions;
    this.blobDeletion = blobDeletion;
    this.clock = clock;
  }

  @Transactional
  public AttachmentView upload(long userId, long cardId, String filename, byte[] content) {
    permissions.require(userId, cardService.requireProjectId(cardId), Permission.ATTACHMENT_CREATE);
    if (attachments.countByCardId(cardId) >= properties.maxPerCard()) {
      throw new AttachmentLimitExceededException(properties.maxPerCard());
    }
    String contentType = contentTypeDetector.detect(content, filename);
    String objectKey = "cards/" + cardId + "/" + UUID.randomUUID();
    // Metadaten zuerst, der Blob-Put als letzter Schritt vor dem Commit (Issue #503): Schlägt der
    // Insert fehl (Constraint, Flush), existiert noch kein Blob — nichts verwaist. Scheitert der
    // Put, rollt die Transaktion die Metadaten zurück. Das verbleibende Restfenster (Commit-
    // Abbruch NACH erfolgreichem Put) hinterlässt eine unsichtbare Blob-Waise ohne kaputten
    // Verweis; die findet der StorageReconciliationService. Die Alternative — Metadaten in einem
    // Zwischenzustand plus zweiter Transaktion — kaufte für genau dieses Restfenster eine
    // Statuslogik in jedem Lesepfad und bräuchte die Reconciliation (Altbestand!) trotzdem.
    Attachment saved =
        attachments.save(
            new Attachment(
                null, cardId, filename, contentType, content.length, objectKey, clock.instant()));
    storage.put(objectKey, content, contentType);
    return view(saved);
  }

  @Transactional(readOnly = true)
  public List<AttachmentView> list(long userId, long cardId) {
    permissions.requireMembership(userId, cardService.requireProjectId(cardId));
    return attachments.findByCardId(cardId).stream().map(AttachmentService::view).toList();
  }

  @Transactional(readOnly = true)
  public Download download(long userId, long attachmentId) {
    Attachment attachment =
        attachments.findById(attachmentId).orElseThrow(AttachmentNotFoundException::new);
    permissions.requireMembership(userId, cardService.requireProjectId(attachment.cardId()));
    return new Download(
        attachment.filename(),
        attachment.contentType(),
        attachment.size(),
        storage.get(attachment.objectKey()));
  }

  @Transactional
  public void delete(long userId, long attachmentId) {
    Attachment attachment =
        attachments.findById(attachmentId).orElseThrow(AttachmentNotFoundException::new);
    permissions.require(
        userId, cardService.requireProjectId(attachment.cardId()), Permission.ATTACHMENT_DELETE);
    // Metadaten sofort, den Blob über die Outbox nachziehen (Issue #503): Löschte man den Blob
    // direkt und bräche der DB-Teil danach ab, zeigte die verbliebene Metadaten-Zeile auf ein
    // nicht mehr existierendes Objekt — der Download endete im Speicherfehler. So herum ist der
    // schlimmste Fall ein kurzzeitig verwaister Blob, den der Worker nach dem Commit entfernt.
    blobDeletion.scheduleDelete(attachment.objectKey());
    attachments.deleteById(attachment.requireId());
  }

  private static AttachmentView view(Attachment a) {
    return new AttachmentView(
        a.requireId(), a.cardId(), a.filename(), a.contentType(), a.size(), a.createdAt());
  }

  /** Metadaten-Darstellung eines Anhangs. */
  public record AttachmentView(
      Long id, Long cardId, String filename, String contentType, long size, Instant createdAt) {}

  /** Download-Ergebnis: Metadaten + Blob-Stream. */
  public record Download(String filename, String contentType, long size, InputStream content) {}
}
