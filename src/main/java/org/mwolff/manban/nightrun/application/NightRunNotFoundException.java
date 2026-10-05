package org.mwolff.manban.nightrun.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Den Lauf gibt es in diesem Projekt nicht (Issue #1375, Plan #1372 E10).
 *
 * <p>Zwei Fälle, eine Antwort: Die ID ist erfunden oder verdrängt, oder der Lauf gehört zu einem
 * anderen Projekt. Wer den Fortschritt liest, erfährt so nichts über Läufe fremder Projekte — kein
 * Existenz-Leak.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NightRunNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;
}
