package org.mwolff.manban.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Verifikations-Token ist unbekannt, bereits eingelöst oder abgelaufen. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidVerificationTokenException extends RuntimeException {

  public InvalidVerificationTokenException() {
    super("Verifikations-Token ist ungültig oder abgelaufen");
  }
}
