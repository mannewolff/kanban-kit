package org.mwolff.manban.accesstoken.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** JPA-Abbildung der Tabelle {@code kanban_access_token}. */
@Entity
@Table(name = "kanban_access_token")
class KanbanAccessTokenEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "project_id")
  private @Nullable Long projectId;

  @Column(name = "board_id")
  private @Nullable Long boardId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "last_used_at")
  private @Nullable Instant lastUsedAt;

  @Column(name = "revoked", nullable = false)
  private boolean revoked;

  protected KanbanAccessTokenEntity() {
    // für JPA
  }

  KanbanAccessTokenEntity(
      @Nullable Long id,
      Long userId,
      @Nullable Long projectId,
      @Nullable Long boardId,
      String name,
      String tokenHash,
      String displayName,
      Instant createdAt,
      @Nullable Instant lastUsedAt,
      boolean revoked) {
    this.id = id;
    this.userId = userId;
    this.projectId = projectId;
    this.boardId = boardId;
    this.name = name;
    this.tokenHash = tokenHash;
    this.displayName = displayName;
    this.createdAt = createdAt;
    this.lastUsedAt = lastUsedAt;
    this.revoked = revoked;
  }

  @Nullable Long getId() {
    return id;
  }

  Long getUserId() {
    return userId;
  }

  @Nullable Long getProjectId() {
    return projectId;
  }

  @Nullable Long getBoardId() {
    return boardId;
  }

  String getName() {
    return name;
  }

  String getTokenHash() {
    return tokenHash;
  }

  String getDisplayName() {
    return displayName;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  @Nullable Instant getLastUsedAt() {
    return lastUsedAt;
  }

  boolean isRevoked() {
    return revoked;
  }
}
