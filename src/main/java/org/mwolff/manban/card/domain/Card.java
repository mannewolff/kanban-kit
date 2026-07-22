package org.mwolff.manban.card.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;

/**
 * Karte — Kern-Aggregat. Die {@code number} ist board-scoped (eindeutig pro Board).
 *
 * <p>Eine Karte gehört immer zu einem Projekt ({@code projectId}). Die Board-Bindung ist optional:
 * eine board-gebundene Karte trägt {@code boardId}, {@code columnId} und {@code number}; eine
 * board-lose Pool-Idee ({@code ideaStored=true}) hat diese drei {@code null} und lebt nur im
 * projektweiten Ideen-Pool. {@code targetBoardId} notiert das gewünschte Zielboard einer Pool-Idee
 * (z. B. aus dem Ingest), ohne sie schon dort einzuplanen.
 *
 * <p>Ein Datensatz ist entweder eine normale Karte ({@link CardType#CARD}) oder ein Epic ({@link
 * CardType#EPIC}). Epics nehmen nicht am Spalten-Workflow teil (keine aktive Position) und können
 * Kinder gruppieren; eine Karte verweist über {@code parentId} auf ihr Epic.
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param boardId zugehöriges Board; {@code null} bei einer board-losen Pool-Idee
 * @param columnId aktuelle Spalte; {@code null} bei einer board-losen Pool-Idee
 * @param number board-scoped Anzeigenummer; {@code null} bei einer board-losen Pool-Idee
 * @param title Titel
 * @param description Markdown-Beschreibung (nullable)
 * @param positionInColumn Position in der Spalte
 * @param archived ob archiviert (dann außerhalb des aktiven Positions-Namespace)
 * @param ideaStored ob im Ideen-Speicher/Pool (dann außerhalb des aktiven Positions-Namespace)
 * @param movedToDoneAt Zeitpunkt des Zugs nach Done (nullable)
 * @param createdBy Ersteller (nullable, z. B. bei PAT)
 * @param createdAt Erstellzeitpunkt
 * @param updatedAt letzte Änderung
 * @param type CARD oder EPIC
 * @param parentId zugeordnetes Epic (nullable; nur an CARD gesetzt)
 * @param shortcode Kürzel eines Epics (nullable; nur an EPIC)
 * @param dueDate Fälligkeitsdatum (nullable; nur an CARD sinnvoll)
 * @param projectId zugehöriges Projekt (immer gesetzt)
 * @param targetBoardId notiertes Zielboard einer Pool-Idee (nullable)
 */
public record Card(
    @Nullable Long id,
    @Nullable Long boardId,
    @Nullable Long columnId,
    @Nullable Integer number,
    String title,
    @Nullable String description,
    int positionInColumn,
    boolean archived,
    boolean ideaStored,
    @Nullable Instant movedToDoneAt,
    @Nullable Long createdBy,
    Instant createdAt,
    Instant updatedAt,
    CardType type,
    @Nullable Long parentId,
    @Nullable String shortcode,
    @Nullable Instant dueDate,
    Long projectId,
    @Nullable Long targetBoardId)
    implements Identifiable {

  /** Board-ID einer board-gebundenen Karte; wirft bei einer board-losen Pool-Idee. */
  public long requireBoardId() {
    if (boardId == null) {
      throw new IllegalStateException("Karte ist board-los (Pool-Idee)");
    }
    return boardId;
  }

  /** Spalten-ID einer board-gebundenen Karte; wirft bei einer board-losen Pool-Idee. */
  public long requireColumnId() {
    if (columnId == null) {
      throw new IllegalStateException("Karte ist board-los (Pool-Idee)");
    }
    return columnId;
  }

  /** Board-scoped Nummer einer board-gebundenen Karte; wirft bei einer board-losen Pool-Idee. */
  public int requireNumber() {
    if (number == null) {
      throw new IllegalStateException("Karte ist board-los (Pool-Idee)");
    }
    return number;
  }

  public Card withContent(String newTitle, @Nullable String newDescription) {
    return new Card(
        id,
        boardId,
        columnId,
        number,
        newTitle,
        newDescription,
        positionInColumn,
        archived,
        ideaStored,
        movedToDoneAt,
        createdBy,
        createdAt,
        updatedAt,
        type,
        parentId,
        shortcode,
        dueDate,
        projectId,
        targetBoardId);
  }

  public Card asArchived() {
    return new Card(
        id,
        boardId,
        columnId,
        number,
        title,
        description,
        positionInColumn,
        true,
        ideaStored,
        movedToDoneAt,
        createdBy,
        createdAt,
        updatedAt,
        type,
        parentId,
        shortcode,
        dueDate,
        projectId,
        targetBoardId);
  }

  /** Wiederherstellen an einer freien Position (append), um Positionskollisionen zu vermeiden. */
  public Card asRestored(int newPositionInColumn) {
    return new Card(
        id,
        boardId,
        columnId,
        number,
        title,
        description,
        newPositionInColumn,
        false,
        ideaStored,
        movedToDoneAt,
        createdBy,
        createdAt,
        updatedAt,
        type,
        parentId,
        shortcode,
        dueDate,
        projectId,
        targetBoardId);
  }

  /**
   * In den Ideen-Speicher legen (analog {@link #asArchived()}); fällt aus dem aktiven Namespace.
   */
  public Card asIdeaStored() {
    return new Card(
        id,
        boardId,
        columnId,
        number,
        title,
        description,
        positionInColumn,
        archived,
        true,
        movedToDoneAt,
        createdBy,
        createdAt,
        updatedAt,
        type,
        parentId,
        shortcode,
        dueDate,
        projectId,
        targetBoardId);
  }

  /**
   * Macht die Karte zu einer board-losen Pool-Idee: {@code board/column/number} entfallen, {@code
   * ideaStored=true}, optional wird das bisherige/gewünschte Board als {@code targetBoardId}
   * notiert.
   */
  public Card asPooledIdea(@Nullable Long newTargetBoardId) {
    return new Card(
        id,
        null,
        null,
        null,
        title,
        description,
        positionInColumn,
        archived,
        true,
        movedToDoneAt,
        createdBy,
        createdAt,
        updatedAt,
        type,
        parentId,
        shortcode,
        dueDate,
        projectId,
        newTargetBoardId);
  }

  /**
   * Plant eine board-lose Idee auf ein Board ein: setzt Board/Spalte/Nummer/Position, {@code
   * ideaStored=false}, und löscht den Zielboard-Hinweis.
   */
  public Card withPlannedOnBoard(
      long newBoardId, long newColumnId, int newNumber, int newPositionInColumn) {
    return new Card(
        id,
        newBoardId,
        newColumnId,
        newNumber,
        title,
        description,
        newPositionInColumn,
        archived,
        false,
        movedToDoneAt,
        createdBy,
        createdAt,
        updatedAt,
        type,
        parentId,
        shortcode,
        dueDate,
        projectId,
        null);
  }

  /**
   * Aus dem Ideen-Speicher ins Backlog holen: {@code ideaStored=false}, mit neuer Spalte (die
   * Backlog-Spalte) und einer freien Position dort. Anders als {@link #asRestored(int)}, das die
   * bisherige Spalte behält, wandert eine promotete Idee bewusst in die erste Spalte.
   */
  public Card asPromoted(int newPositionInColumn, long newColumnId) {
    return new Card(
        id,
        boardId,
        newColumnId,
        number,
        title,
        description,
        newPositionInColumn,
        archived,
        false,
        movedToDoneAt,
        createdBy,
        createdAt,
        updatedAt,
        type,
        parentId,
        shortcode,
        dueDate,
        projectId,
        targetBoardId);
  }

  public Card withMovedToDoneAt(@Nullable Instant when) {
    return new Card(
        id,
        boardId,
        columnId,
        number,
        title,
        description,
        positionInColumn,
        archived,
        ideaStored,
        when,
        createdBy,
        createdAt,
        updatedAt,
        type,
        parentId,
        shortcode,
        dueDate,
        projectId,
        targetBoardId);
  }

  /** Setzt oder löscht ({@code null}) die Epic-Zuordnung. */
  public Card withParent(@Nullable Long newParentId) {
    return new Card(
        id,
        boardId,
        columnId,
        number,
        title,
        description,
        positionInColumn,
        archived,
        ideaStored,
        movedToDoneAt,
        createdBy,
        createdAt,
        updatedAt,
        type,
        newParentId,
        shortcode,
        dueDate,
        projectId,
        targetBoardId);
  }

  /** Setzt das Kürzel (nur für Epics sinnvoll). */
  public Card withShortcode(@Nullable String newShortcode) {
    return new Card(
        id,
        boardId,
        columnId,
        number,
        title,
        description,
        positionInColumn,
        archived,
        ideaStored,
        movedToDoneAt,
        createdBy,
        createdAt,
        updatedAt,
        type,
        parentId,
        newShortcode,
        dueDate,
        projectId,
        targetBoardId);
  }

  /** Setzt oder löscht ({@code null}) das Fälligkeitsdatum. */
  public Card withDueDate(@Nullable Instant newDueDate) {
    return new Card(
        id,
        boardId,
        columnId,
        number,
        title,
        description,
        positionInColumn,
        archived,
        ideaStored,
        movedToDoneAt,
        createdBy,
        createdAt,
        updatedAt,
        type,
        parentId,
        shortcode,
        newDueDate,
        projectId,
        targetBoardId);
  }
}
