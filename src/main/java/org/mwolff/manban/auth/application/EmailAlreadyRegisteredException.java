package org.mwolff.manban.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Registrierung mit einer bereits vergebenen E-Mail-Adresse. */
@ResponseStatus(HttpStatus.CONFLICT)
public class EmailAlreadyRegisteredException extends RuntimeException {

  public EmailAlreadyRegisteredException() {
    super("E-Mail-Adresse ist bereits registriert");
  }
}
