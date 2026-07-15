package org.mwolff.manban.card.domain;

import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;

/**
 * Board-scoped Label (Tag) mit Name und Farbe. Labels kategorisieren Karten quer zu den Epics.
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param boardId zugehöriges Board
 * @param name Anzeigename (eindeutig pro Board)
 * @param color Farbe (z. B. Hex oder Theme-Token)
 */
public record Label(@Nullable Long id, Long boardId, String name, String color)
    implements Identifiable {

  public Label withContent(String newName, String newColor) {
    return new Label(id, boardId, newName, newColor);
  }
}
