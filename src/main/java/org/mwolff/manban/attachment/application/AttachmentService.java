package org.mwolff.manban.attachment.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.mwolff.manban.attachment.domain.Attachment;
import org.mwolff.manban.card.application.Anbietersicht;
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.project.application.BoardQuelle;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anhang-Use-Cases. Der Blob wandert in den Objektspeicher (SeaweedFS), nur Metadaten in die DB.
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
 * </ul>
 *
 * <p>Die frühere Auflösung über das Board (gelöschtes {@code projectIdOfCard}) lieferte in diesem
 * Fall 404 bzw. 500 und machte Anhänge zum einzigen Ausreißer gegenüber {@code CommentService}, der
 * schon immer projekt-basiert prüft.
 *
 * <p>Schreibsperre bei fremdem Board (Issue #2164, Plan #2152 A10): Hochladen und Löschen lehnen
 * nach der Rechteprüfung mit {@link org.mwolff.manban.project.application.FremdesBoardException}
 * ab, wenn das Board des Projekts in einem fremden System liegt; Lesen bleibt möglich.
 *
 * <p>Störungen beim Anbieter (Issue #2214, Plan #2206 E2): Ohne das Recht {@link
 * Permission#REPAIR_READ} sind die Anhänge einer verborgenen Karte nicht da — die Liste und das
 * Hochladen enden wie an einer unbekannten Karte, Herunterladen und Löschen wie bei einem
 * unbekannten Anhang ({@link Anbietersicht#requireSichtbar}).
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
  private final BoardQuelle boardQuelle;
  private final Anbietersicht anbieter;

  public AttachmentService(
      AttachmentRepository attachments,
      ObjectStorage storage,
      ContentTypeDetector contentTypeDetector,
      ObjectStorageProperties properties,
      CardService cardService,
      PermissionChecker permissions,
      BlobDeletionScheduler blobDeletion,
      Clock clock,
      BoardQuelle boardQuelle,
      Anbietersicht anbieter) {
    this.attachments = attachments;
    this.storage = storage;
    this.contentTypeDetector = contentTypeDetector;
    this.properties = properties;
    this.cardService = cardService;
    this.permissions = permissions;
    this.blobDeletion = blobDeletion;
    this.clock = clock;
    this.boardQuelle = boardQuelle;
    this.anbieter = anbieter;
  }

  @Transactional
  public AttachmentView upload(long userId, long cardId, String filename, byte[] content) {
    long projectId = cardService.requireProjectId(cardId);
    permissions.require(userId, projectId, Permission.ATTACHMENT_CREATE);
    anbieter.requireSichtbar(userId, cardId);
    boardQuelle.pruefeSchreibbar(projectId);
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
    anbieter.requireSichtbar(userId, cardId);
    return attachments.findByCardId(cardId).stream().map(AttachmentService::view).toList();
  }

  @Transactional(readOnly = true)
  public Download download(long userId, long attachmentId) {
    Attachment attachment =
        attachments.findById(attachmentId).orElseThrow(AttachmentNotFoundException::new);
    return deliver(userId, attachment);
  }

  /**
   * Download über die Karte adressiert (Issue #1697). Hängt der Anhang an einer anderen Karte,
   * lautet die Antwort wie für einen unbekannten Anhang — geprüft, bevor Mitgliedschaft und
   * Objektspeicher berührt werden, damit der Aufrufer nicht erfährt, ob er existiert.
   */
  @Transactional(readOnly = true)
  public Download downloadOnCard(long userId, long cardId, long attachmentId) {
    Attachment attachment =
        attachments
            .findById(attachmentId)
            .filter(a -> a.cardId() == cardId)
            .orElseThrow(AttachmentNotFoundException::new);
    return deliver(userId, attachment);
  }

  private Download deliver(long userId, Attachment attachment) {
    permissions.requireMembership(userId, cardService.requireProjectId(attachment.cardId()));
    requireSichtbar(userId, attachment);
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
    long projectId = cardService.requireProjectId(attachment.cardId());
    permissions.require(userId, projectId, Permission.ATTACHMENT_DELETE);
    requireSichtbar(userId, attachment);
    boardQuelle.pruefeSchreibbar(projectId);
    // Metadaten sofort, den Blob über die Outbox nachziehen (Issue #503): Löschte man den Blob
    // direkt und bräche der DB-Teil danach ab, zeigte die verbliebene Metadaten-Zeile auf ein
    // nicht mehr existierendes Objekt — der Download endete im Speicherfehler. So herum ist der
    // schlimmste Fall ein kurzzeitig verwaister Blob, den der Worker nach dem Commit entfernt.
    blobDeletion.scheduleDelete(attachment.objectKey());
    attachments.deleteById(attachment.requireId());
  }

  /** Ein Anhang an einer verborgenen Karte endet wie ein unbekannter (kein Existenz-Leak). */
  private void requireSichtbar(long userId, Attachment attachment) {
    try {
      anbieter.requireSichtbar(userId, attachment.cardId());
    } catch (CardNotFoundException e) {
      throw new AttachmentNotFoundException(e);
    }
  }

  private static AttachmentView view(Attachment a) {
    return new AttachmentView(
        a.requireId(), a.cardId(), a.filename(), a.contentType(), a.size(), a.createdAt());
  }

  /** Metadaten-Darstellung eines Anhangs. */
  @Schema(description = "Die Metadaten eines Anhangs, ohne Inhalt.")
  public record AttachmentView(
      @Schema(description = "Interne ID des Anhangs.", example = "91") Long id,
      @Schema(description = "Interne ID der Karte.", example = "812") Long cardId,
      @Schema(description = "Dateiname beim Hochladen.", example = "skizze.png") String filename,
      @Schema(description = "Aus dem Inhalt erkannter Medientyp.", example = "image/png")
          String contentType,
      @Schema(description = "Größe in Byte.", example = "48213") long size,
      @Schema(description = "Zeitpunkt des Hochladens.") Instant createdAt) {}

  /** Download-Ergebnis: Metadaten + Blob-Stream. */
  public record Download(String filename, String contentType, long size, InputStream content) {}
}
