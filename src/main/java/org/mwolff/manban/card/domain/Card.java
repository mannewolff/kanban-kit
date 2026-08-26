package org.mwolff.manban.card.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;

/**
 * Karte — Kern-Aggregat. Die {@code number} ist board-scoped (eindeutig pro Board).
 *
 * <p>Eine Karte gehört immer zu einem Projekt ({@code projectId}). Die Board-Bindung ist optional:
 * eine board-gebundene Karte trägt {@code boardId} und {@code columnId}; eine board-lose Pool-Idee
 * ({@code ideaStored=true}) hat beide {@code null} und lebt nur im projektweiten Ideen-Pool. Die
 * projektweite {@code number} bleibt beim Weg in den Pool erhalten (#433) — sie ist seit #402
 * sofort vergeben und wird nur bei Alt-Ideen aus der Zeit davor vermisst. {@code targetBoardId}
 * notiert das gewünschte Zielboard einer Pool-Idee (z. B. aus dem Ingest oder dem Ideen-Speicher),
 * ohne sie schon dort einzuplanen.
 *
 * <p>Ein Datensatz ist entweder eine normale Karte ({@link CardType#CARD}) oder ein Vorhaben
 * ({@link CardType#EPIC}). Vorhaben nehmen nicht am Spalten-Workflow teil (keine aktive Position)
 * und können Kinder gruppieren; eine Karte verweist über {@code parentId} auf ihr Vorhaben.
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param boardId zugehöriges Board; {@code null} bei einer board-losen Pool-Idee
 * @param columnId aktuelle Spalte; {@code null} bei einer board-losen Pool-Idee
 * @param number board-scoped Anzeigenummer; {@code null} nur bei Alt-Ideen von vor #402
 * @param title Titel
 * @param description Markdown-Beschreibung (nullable)
 * @param positionInColumn Position in der Spalte
 * @param archived ob archiviert (dann außerhalb des aktiven Positions-Namespace)
 * @param ideaStored ob im Ideen-Speicher/Pool (dann außerhalb des aktiven Positions-Namespace)
 * @param movedToDoneAt Zeitpunkt des Zugs nach Done (nullable)
 * @param createdBy Ersteller (nullable, z. B. bei PAT)
 * @param createdAt Erstellzeitpunkt
 * @param updatedAt letzte Änderung
 * @param type CARD oder EPIC (gespeicherter Wert des Vorhabens, siehe {@link CardType})
 * @param parentId zugeordnetes Vorhaben (nullable; nur an CARD gesetzt)
 * @param shortcode Kürzel eines Vorhabens (nullable; nur an EPIC)
 * @param dueDate Fälligkeitsdatum (nullable; nur an CARD sinnvoll)
 * @param projectId zugehöriges Projekt (immer gesetzt)
 * @param targetBoardId notiertes Zielboard einer Pool-Idee (nullable)
 * @param externalKey idempotenz-Schlüssel eines Automatik-Ingests (nullable, projekt-eindeutig, z.
 *     B. {@code sonar:<issue-key>}; Issue #534) — verhindert Doppel-Anlage durch wiederholte Läufe,
 *     solange die Karte existiert (auch archiviert/Papierkorb); purge gibt ihn frei
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
    @Nullable Long targetBoardId,
    @Nullable String externalKey)
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
        targetBoardId,
        externalKey);
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
        targetBoardId,
        externalKey);
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
        targetBoardId,
        externalKey);
  }

  /**
   * Macht die Karte zu einer board-losen Pool-Idee: {@code board/column} entfallen, {@code
   * ideaStored=true}, optional wird das bisherige/gewünschte Board als {@code targetBoardId}
   * notiert. Die projektweite {@code number} bleibt erhalten (#433) — sie ist seit #402 sofort
   * vergeben, und Rückverweise (#N) auf die Karte dürfen beim Weg in den Pool nicht brechen.
   */
  public Card asPooledIdea(@Nullable Long newTargetBoardId) {
    return new Card(
        id,
        null,
        null,
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
        newTargetBoardId,
        externalKey);
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
        null,
        externalKey);
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
        targetBoardId,
        externalKey);
  }

  /** Setzt oder löscht ({@code null}) die Vorhaben-Zuordnung. */
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
        targetBoardId,
        externalKey);
  }

  /** Setzt das Kürzel (nur für Vorhaben sinnvoll). */
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
        targetBoardId,
        externalKey);
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
        targetBoardId,
        externalKey);
  }
}
