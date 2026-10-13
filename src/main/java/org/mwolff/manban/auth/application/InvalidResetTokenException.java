package org.mwolff.manban.auth.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Passwort-Reset-Token ist unbekannt, bereits eingelöst oder abgelaufen. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidResetTokenException extends RuntimeException implements UebersetzbareMeldung {

  public InvalidResetTokenException() {
    super("Reset-Token ist ungültig oder abgelaufen");
  }

  @Override
  public String schluessel() {
    return "auth.reset-token-ungueltig";
  }
}
