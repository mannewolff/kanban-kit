package org.mwolff.manban.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Ein Admin kann sein eigenes Konto nicht sperren (400). */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class CannotDisableSelfException extends RuntimeException {

  public CannotDisableSelfException() {
    super("Das eigene Konto kann nicht gesperrt werden");
  }
}
