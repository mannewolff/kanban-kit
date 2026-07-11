package org.mwolff.manban.board.domain;

import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;

/**
 * Spalte eines Boards. Spalten sind pro Board konfigurierbar (kein festes Enum).
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param boardId zugehöriges Board
 * @param name Spaltenname
 * @param position Reihenfolge innerhalb des Boards (0-basiert)
 * @param wipLimit optionales WIP-Limit; {@code null} = kein Limit
 */
public record BoardColumn(
    @Nullable Long id, Long boardId, String name, int position, @Nullable Integer wipLimit)
    implements Identifiable {

  public BoardColumn with(String newName, @Nullable Integer newWipLimit) {
    return new BoardColumn(id, boardId, newName, position, newWipLimit);
  }
}
