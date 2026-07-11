package org.mwolff.manban.attachment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** JPA-Abbildung der Tabelle {@code attachment_meta}. */
@Entity
@Table(name = "attachment_meta")
class AttachmentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "card_id", nullable = false)
  private Long cardId;

  @Column(name = "filename", nullable = false)
  private String filename;

  @Column(name = "content_type", nullable = false)
  private String contentType;

  @Column(name = "size", nullable = false)
  private long size;

  @Column(name = "object_key", nullable = false)
  private String objectKey;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AttachmentEntity() {
    // für JPA
  }

  AttachmentEntity(
      @Nullable Long id,
      Long cardId,
      String filename,
      String contentType,
      long size,
      String objectKey,
      Instant createdAt) {
    this.id = id;
    this.cardId = cardId;
    this.filename = filename;
    this.contentType = contentType;
    this.size = size;
    this.objectKey = objectKey;
    this.createdAt = createdAt;
  }

  @Nullable Long getId() {
    return id;
  }

  Long getCardId() {
    return cardId;
  }

  String getFilename() {
    return filename;
  }

  String getContentType() {
    return contentType;
  }

  long getSize() {
    return size;
  }

  String getObjectKey() {
    return objectKey;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
