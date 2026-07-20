package org.mwolff.manban.card.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.domain.Card;

/**
 * JPA-Abbildung der Tabelle {@code card}. Die generierte Spalte {@code active_position} wird von
 * der Datenbank verwaltet (STORED) und daher NICHT gemappt.
 */
// PMD.TooManyFields: 1:1-Abbildung der Tabelle card; die Feldzahl folgt den Spalten, kein Smell.
@SuppressWarnings("PMD.TooManyFields")
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

  @Column(name = "idea_stored", nullable = false)
  private boolean ideaStored;

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

  @Column(name = "due_date")
  private @Nullable Instant dueDate;

  // Soft-Delete (Issue #179): nur für die JPA-Filterung ({@code deletedAt is null}) gemappt; der
  // Papierkorb-Lebenszyklus (löschen/wiederherstellen/purge) läuft über den Adapter per SQL, nicht
  // über den Card-Domain-Record.
  @Column(name = "deleted_at")
  private @Nullable Instant deletedAt;

  protected CardEntity() {
    // für JPA
  }

  /** Baut die Entity direkt aus dem Domänenobjekt (statt aus 15 Einzelparametern, Sonar S107). */
  CardEntity(Card c) {
    this.id = c.id();
    this.boardId = c.boardId();
    this.columnId = c.columnId();
    this.number = c.number();
    this.title = c.title();
    this.description = c.description();
    this.positionInColumn = c.positionInColumn();
    this.archived = c.archived();
    this.ideaStored = c.ideaStored();
    this.movedToDoneAt = c.movedToDoneAt();
    this.createdBy = c.createdBy();
    this.createdAt = c.createdAt();
    this.updatedAt = c.updatedAt();
    this.type = c.type().name();
    this.parentId = c.parentId();
    this.shortcode = c.shortcode();
    this.dueDate = c.dueDate();
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

  boolean isIdeaStored() {
    return ideaStored;
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

  @Nullable Instant getDueDate() {
    return dueDate;
  }

  @Nullable Instant getDeletedAt() {
    return deletedAt;
  }
}
