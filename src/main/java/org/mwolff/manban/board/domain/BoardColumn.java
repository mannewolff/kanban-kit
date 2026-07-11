package org.mwolff.manban.board.domain;

/**
 * Spalte eines Boards. Spalten sind pro Board konfigurierbar (kein festes Enum).
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param boardId zugehöriges Board
 * @param name Spaltenname
 * @param position Reihenfolge innerhalb des Boards (0-basiert)
 * @param wipLimit optionales WIP-Limit; {@code null} = kein Limit
 */
public record BoardColumn(Long id, Long boardId, String name, int position, Integer wipLimit) {

  public BoardColumn with(String newName, Integer newWipLimit) {
    return new BoardColumn(id, boardId, newName, position, newWipLimit);
  }
}
