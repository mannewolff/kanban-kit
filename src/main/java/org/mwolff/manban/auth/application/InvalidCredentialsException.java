package org.mwolff.manban.auth.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Falsche Anmeldedaten. Bewusst ohne Preisgabe, ob die E-Mail existiert. */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidCredentialsException extends RuntimeException implements UebersetzbareMeldung {

  public InvalidCredentialsException() {
    super("Ungültige Anmeldedaten");
  }

  @Override
  public String schluessel() {
    return "auth.anmeldedaten-ungueltig";
  }
}
