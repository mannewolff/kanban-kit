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
 */
@Service
public class AttachmentService {

  private final AttachmentRepository attachments;
  private final ObjectStorage storage;
  private final ContentTypeDetector contentTypeDetector;
  private final ObjectStorageProperties properties;
  private final CardService cardService;
  private final PermissionChecker permissions;
  private final Clock clock;

  public AttachmentService(
      AttachmentRepository attachments,
      ObjectStorage storage,
      ContentTypeDetector contentTypeDetector,
      ObjectStorageProperties properties,
      CardService cardService,
      PermissionChecker permissions,
      Clock clock) {
    this.attachments = attachments;
    this.storage = storage;
    this.contentTypeDetector = contentTypeDetector;
    this.properties = properties;
    this.cardService = cardService;
    this.permissions = permissions;
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
    storage.put(objectKey, content, contentType);
    Attachment saved =
        attachments.save(
            new Attachment(
                null, cardId, filename, contentType, content.length, objectKey, clock.instant()));
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
    storage.delete(attachment.objectKey());
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
