package org.mwolff.manban.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Login abgelehnt, weil der Benutzer noch nicht von einem Plattform-Admin freigegeben wurde.
 *
 * <p>Die Message wird vom {@code GlobalExceptionHandler} unverändert zum {@code detail} des
 * RFC-9457-Problem-Details und vom Frontend als Login-Fehler angezeigt. Sie nennt deshalb neben dem
 * Grund auch den Ausweg (Issue #560): Für einen Selbst-Hoster ohne freigebenden Admin war der reine
 * Grund eine Sackgasse. Bewusst ohne technische Details und ohne Aussage darüber, ob die
 * E-Mail-Adresse registriert ist — die Meldung erscheint ohnehin nur nach korrektem Passwort und
 * gibt damit nichts preis, was der Aufrufer nicht schon wüsste.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class UserNotApprovedException extends RuntimeException {

  public UserNotApprovedException() {
    super(
        "Das Konto wartet auf die Freigabe durch einen Plattform-Admin."
            + " Auf einer frisch aufgesetzten Instanz richten Sie den ersten Plattform-Admin"
            + " nach der Anleitung in docs/betrieb.md ein.");
  }
}
