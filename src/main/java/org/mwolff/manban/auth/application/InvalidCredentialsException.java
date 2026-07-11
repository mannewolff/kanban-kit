package org.mwolff.manban.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Falsche Anmeldedaten. Bewusst ohne Preisgabe, ob die E-Mail existiert. */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("Ungültige Anmeldedaten");
  }
}
