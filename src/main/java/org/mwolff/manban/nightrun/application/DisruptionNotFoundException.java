package org.mwolff.manban.nightrun.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der Lauf, der quittiert werden soll, gibt es für den Plattform-Leitstand nicht (Issue #1080, Plan
 * #1072 E11, E12).
 *
 * <p>Drei Fälle, eine Antwort: Den Lauf hat es nie gegeben, der Ringpuffer hat ihn verdrängt, oder
 * sein Projekt nimmt nicht (mehr) teil. Für den Quittierenden ist das dasselbe — die Zeile, die er
 * wegräumen wollte, ist nicht (mehr) seine.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class DisruptionNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;
}
