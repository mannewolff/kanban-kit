package org.mwolff.manban.codeanalyse.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Ein Zugang ohne Board-Bindung kann keine Messung melden (Issue #2275, Plan #2272 E5, fachlich
 * #2271 AK 9).
 *
 * <p>Das Projekt kommt aus der Bindung des Zugangs, nie aus dem Aufruf; ohne Bindung gibt es kein
 * Zielprojekt, und nichts wird gespeichert. {@code 409} wie die übrige Kanban-Schnittstelle unter
 * {@code /api/kanban/**}.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class TokenNotBoundForMessungException extends RuntimeException
    implements UebersetzbareMeldung {

  public TokenNotBoundForMessungException() {
    super(
        "Dieser Zugang gehört zu keinem Board. Bitte einen Zugang verwenden, der an ein Board"
            + " gebunden ist.");
  }

  @Override
  public String schluessel() {
    return "codeanalyse.token-ohne-board";
  }
}
