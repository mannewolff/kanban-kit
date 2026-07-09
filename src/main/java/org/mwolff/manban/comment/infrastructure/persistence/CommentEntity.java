package org.mwolff.manban.comment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** JPA-Abbildung der Tabelle {@code comment}. */
@Entity
@Table(name = "comment")
class CommentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(name = "author_user_id")
    private Long authorUserId;

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

    CommentEntity(Long id, Long cardId, Long authorUserId, String authorName, String body,
                  Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.cardId = cardId;
        this.authorUserId = authorUserId;
        this.authorName = authorName;
        this.body = body;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    Long getId() {
        return id;
    }

    Long getCardId() {
        return cardId;
    }

    Long getAuthorUserId() {
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
