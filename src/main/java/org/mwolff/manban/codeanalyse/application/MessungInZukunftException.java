package org.mwolff.manban.codeanalyse.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der Zeitpunkt der Veröffentlichung liegt mehr als fünf Minuten nach der Serverzeit (Issue #2275,
 * Plan #2272 E8). Eine Messung aus der Zukunft landete in einer Woche, die noch nicht begonnen hat;
 * gespeichert wird nichts, sodass eine berichtigte Meldung angenommen wird.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class MessungInZukunftException extends RuntimeException implements UebersetzbareMeldung {

  public MessungInZukunftException() {
    super("Der Zeitpunkt der Veröffentlichung liegt mehr als 5 Minuten in der Zukunft.");
  }

  @Override
  public String schluessel() {
    return "codeanalyse.in-zukunft";
  }
}
