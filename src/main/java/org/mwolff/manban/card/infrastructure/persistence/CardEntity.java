package org.mwolff.manban.card.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * JPA-Abbildung der Tabelle {@code card}. Die generierte Spalte {@code active_position} wird von
 * der Datenbank verwaltet (STORED) und daher NICHT gemappt.
 */
@Entity
@Table(name = "card")
class CardEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "board_id", nullable = false)
  private Long boardId;

  @Column(name = "column_id", nullable = false)
  private Long columnId;

  @Column(name = "number", nullable = false)
  private int number;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "description")
  private @Nullable String description;

  @Column(name = "position_in_column", nullable = false)
  private int positionInColumn;

  @Column(name = "archived", nullable = false)
  private boolean archived;

  @Column(name = "moved_to_done_at")
  private @Nullable Instant movedToDoneAt;

  @Column(name = "created_by")
  private @Nullable Long createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "type", nullable = false)
  private String type;

  @Column(name = "parent_id")
  private @Nullable Long parentId;

  @Column(name = "shortcode")
  private @Nullable String shortcode;

  protected CardEntity() {
    // für JPA
  }

  CardEntity(
      @Nullable Long id,
      Long boardId,
      Long columnId,
      int number,
      String title,
      @Nullable String description,
      int positionInColumn,
      boolean archived,
      @Nullable Instant movedToDoneAt,
      @Nullable Long createdBy,
      Instant createdAt,
      Instant updatedAt,
      String type,
      @Nullable Long parentId,
      @Nullable String shortcode) {
    this.id = id;
    this.boardId = boardId;
    this.columnId = columnId;
    this.number = number;
    this.title = title;
    this.description = description;
    this.positionInColumn = positionInColumn;
    this.archived = archived;
    this.movedToDoneAt = movedToDoneAt;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.type = type;
    this.parentId = parentId;
    this.shortcode = shortcode;
  }

  @Nullable Long getId() {
    return id;
  }

  Long getBoardId() {
    return boardId;
  }

  Long getColumnId() {
    return columnId;
  }

  int getNumber() {
    return number;
  }

  String getTitle() {
    return title;
  }

  @Nullable String getDescription() {
    return description;
  }

  int getPositionInColumn() {
    return positionInColumn;
  }

  boolean isArchived() {
    return archived;
  }

  @Nullable Instant getMovedToDoneAt() {
    return movedToDoneAt;
  }

  @Nullable Long getCreatedBy() {
    return createdBy;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }

  String getType() {
    return type;
  }

  @Nullable Long getParentId() {
    return parentId;
  }

  @Nullable String getShortcode() {
    return shortcode;
  }
}
