package org.mwolff.manban.comment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** JPA-Abbildung der Tabelle {@code comment}. */
@Entity
@Table(name = "comment")
class CommentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "card_id", nullable = false)
  private Long cardId;

  @Column(name = "author_user_id")
  private @Nullable Long authorUserId;

  @Column(name = "author_name", nullable = false)
  private String authorName;

  @Column(name = "body", nullable = false)
  private String body;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected CommentEntity() {
    // für JPA
  }

  CommentEntity(
      @Nullable Long id,
      Long cardId,
      @Nullable Long authorUserId,
      String authorName,
      String body,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.cardId = cardId;
    this.authorUserId = authorUserId;
    this.authorName = authorName;
    this.body = body;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  @Nullable Long getId() {
    return id;
  }

  Long getCardId() {
    return cardId;
  }

  @Nullable Long getAuthorUserId() {
    return authorUserId;
  }

  String getAuthorName() {
    return authorName;
  }

  String getBody() {
    return body;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }
}
