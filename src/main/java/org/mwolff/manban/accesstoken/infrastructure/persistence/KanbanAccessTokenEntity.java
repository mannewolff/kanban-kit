package org.mwolff.manban.accesstoken.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.domain.AccessToken;

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

  /** Baut die Entity direkt aus dem Domänenobjekt (statt aus 10 Einzelparametern, Sonar S107). */
  KanbanAccessTokenEntity(AccessToken t) {
    this.id = t.id();
    this.userId = t.userId();
    this.projectId = t.projectId();
    this.boardId = t.boardId();
    this.name = t.name();
    this.tokenHash = t.tokenHash();
    this.displayName = t.displayName();
    this.createdAt = t.createdAt();
    this.lastUsedAt = t.lastUsedAt();
    this.revoked = t.revoked();
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
