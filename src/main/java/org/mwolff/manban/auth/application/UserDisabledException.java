package org.mwolff.manban.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Das Konto wurde von einem Admin gesperrt (403). */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class UserDisabledException extends RuntimeException {

  public UserDisabledException() {
    super("Konto gesperrt");
  }
}
