package org.mwolff.manban.card.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Operation braucht die projektweite Nummer der Karte, diese trägt aber keine (Issue #566).
 *
 * <p>Betrifft nur Alt-Bestand: Seit #402 bekommt jede neu angelegte Karte sofort eine Nummer, auch
 * die board-lose Pool-Idee. Ältere Pool-Ideen aus der Zeit davor haben {@code number == null} und
 * bekommen eine erst beim Einplanen.
 *
 * <p>422 statt 400, weil der Request formal in Ordnung ist — es ist der Zustand der Zielkarte, der
 * die Operation unmöglich macht. Ohne diese Prüfung liefe die Selbstverweis-Prüfung über {@code
 * requireNumber()} in eine Exception statt in eine definierte Antwort.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class CardWithoutNumberException extends RuntimeException {

  public CardWithoutNumberException() {
    super("Die Karte hat keine projektweite Nummer und kann keine Abhaengigkeiten tragen.");
  }
}
