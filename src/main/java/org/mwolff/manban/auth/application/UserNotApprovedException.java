package org.mwolff.manban.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Login abgelehnt, weil der Benutzer noch nicht von einem Plattform-Admin freigegeben wurde.
 *
 * <p>Die Message wird vom {@code GlobalExceptionHandler} unverändert zum {@code detail} des
 * RFC-9457-Problem-Details und vom Frontend als Login-Fehler angezeigt. Sie nennt deshalb neben dem
 * Grund auch den Ausweg — der reine Grund war eine Sackgasse.
 *
 * <p>Der Ausweg verweist an den Betreiber der Instanz (Issue #562) und nicht mehr auf die
 * Einrichtung eines ersten Admins: Diese Ausnahme fliegt nur, wenn bereits ein Plattform-Admin
 * existiert (siehe {@code LoginService}) — es gibt also stets jemanden, der freigeben kann.
 * Existiert noch kein Admin, greift die Aussperr-Ausnahme des Login-Gates und die Anmeldung
 * gelingt; die Meldung erscheint dann gar nicht. Ein Hinweis auf {@code docs/betrieb.md} beschriebe
 * damit einen Zustand, den ihr Empfänger nie erlebt, und exponierte ein Repo-internes Dokument an
 * ein Publikum, das nichts damit anfangen kann.
 *
 * <p>Bewusst ohne technische Details und ohne Aussage darüber, ob die E-Mail-Adresse registriert
 * ist — die Meldung erscheint ohnehin nur nach korrektem Passwort und gibt damit nichts preis, was
 * der Aufrufer nicht schon wüsste.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class UserNotApprovedException extends RuntimeException {

  public UserNotApprovedException() {
    super(
        "Das Konto wartet auf die Freigabe durch einen Plattform-Admin."
            + " Bitte wenden Sie sich an den Betreiber dieser Instanz.");
  }
}
