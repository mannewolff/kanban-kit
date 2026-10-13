package org.mwolff.manban.kanbancompat.application;

import java.io.Serializable;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der angeforderte Kanban-Spalten-Key ist unbekannt oder das gebundene Board hat keine passende
 * Spalte. Ergibt HTTP 400.
 *
 * <p>Je Fall eine Fabrikmethode mit eigenem Meldungsschlüssel (Issue #1641).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public final class InvalidKanbanColumnException extends RuntimeException
    implements UebersetzbareMeldung {

  private final String meldungsschluessel;
  private final Serializable[] meldungsargumente;

  private InvalidKanbanColumnException(String schluessel, String text, Serializable... argumente) {
    super(text);
    this.meldungsschluessel = schluessel;
    this.meldungsargumente = argumente;
  }

  /** Karten lassen sich nicht in DONE anlegen. */
  public static InvalidKanbanColumnException doneNichtAnlegbar() {
    return new InvalidKanbanColumnException(
        "kanbancompat.spalte.done-nicht-anlegbar",
        "Karten koennen nicht in DONE angelegt werden — dass eine Karte fertig ist, stellt ein"
            + " Mensch auf dem Board fest.");
  }

  /** Der Schlüssel ist keiner der Kanban-Spalten-Keys. */
  public static InvalidKanbanColumnException unbekannt(@Nullable String key) {
    String angegeben = String.valueOf(key);
    return new InvalidKanbanColumnException(
        "kanbancompat.spalte.unbekannt", "Unbekannte Kanban-Spalte: " + angegeben, angegeben);
  }

  /** Das Board hat keine Spalte für den Schlüssel. */
  public static InvalidKanbanColumnException fehltAufBoard(long boardId, String key) {
    return new InvalidKanbanColumnException(
        "kanbancompat.spalte.fehlt-auf-board",
        "Board " + boardId + " hat keine Spalte für " + key,
        boardId,
        key);
  }

  @Override
  public String schluessel() {
    return meldungsschluessel;
  }

  @Override
  public Object[] argumente() {
    return meldungsargumente.clone();
  }
}
