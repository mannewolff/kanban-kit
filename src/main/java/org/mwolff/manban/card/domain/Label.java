package org.mwolff.manban.card.domain;

import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;

/**
 * Board-scoped Label (Tag) mit Name und Farbe. Labels kategorisieren Karten quer zu den Vorhaben.
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param boardId zugehöriges Board
 * @param name Anzeigename (eindeutig pro Board)
 * @param color Farbe (z. B. Hex oder Theme-Token)
 * @param countOnEpicTile ob dieses Label auf der Vorhaben-Kachel als Marke gezählt wird; welche
 *     Labels das sind, entscheidet der Betreiber je Board (Issue #659). Standard ist {@code false}
 *     — ein Bestandsboard soll nach der Migration nicht plötzlich alle Labels auf den Kacheln
 *     zeigen.
 */
public record Label(
    @Nullable Long id, Long boardId, String name, String color, boolean countOnEpicTile)
    implements Identifiable {

  public Label withContent(String newName, String newColor, boolean newCountOnEpicTile) {
    return new Label(id, boardId, newName, newColor, newCountOnEpicTile);
  }
}
