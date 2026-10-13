package org.mwolff.manban.kanbancompat.web;

import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.kanbancompat.application.TokenNotBoundException;
import org.springframework.security.core.Authentication;

/**
 * Geteilte Teile der Controller der Kanban-Strecke (Issue #1696, Plan #1694): die Extraktion des
 * {@link KanbanPrincipal} aus der Authentication, die gemeinsamen Beschreibungstexte und der
 * OpenAPI-Tag.
 */
final class KanbanAuthentifizierung {

  static final String TAG_NAME = "Einliefern (Kanban-kompatibel)";

  static final String TAG_BESCHREIBUNG =
      "Karten, Kommentare und Labels über ein board-gebundenes Projekt-Token lesen und"
          + " schreiben und die Anhänge einer Karte lesen, ohne Anmeldung im Browser. Hochladen"
          + " und Löschen von Anhängen gibt es über das Token nicht. Das Board kommt aus der"
          + " Bindung des Tokens (Header X-Kanban-Token), nicht aus dem Aufruf: Jeder Aufruf"
          + " wirkt nur auf das"
          + " Board, für das das Token ausgestellt wurde, und das Token darf nie mehr als die"
          + " Person, die es angelegt hat. Spalten heißen hier mit festen Schlüsseln BACKLOG,"
          + " READY, IN_PROGRESS, IN_REVIEW und DONE.";

  static final String TOKEN_BINDUNG =
      "Das Board kommt aus der Bindung des Projekt-Tokens; ein Token ohne Board-Bindung wird"
          + " mit 409 abgewiesen.";

  static final String BESCHREIBUNG_ID =
      "Interne ID der Karte (Feld id aus GET /api/kanban/items), nicht die projektweite Nummer.";

  static final String NICHT_GEBUNDEN =
      "Das Projekt-Token ist an kein Board gebunden; ein board-gebundenes Token verwenden.";

  static final String KARTE_FEHLT =
      "Die Karte gibt es nicht, oder sie liegt nicht auf dem gebundenen Board.";

  private KanbanAuthentifizierung() {}

  /** Der Principal aus den Details der Authentication; ohne Board-Bindung 409. */
  static KanbanPrincipal principal(@Nullable Authentication authentication) {
    if (authentication != null && authentication.getDetails() instanceof KanbanPrincipal p) {
      return p;
    }
    throw new TokenNotBoundException();
  }
}
