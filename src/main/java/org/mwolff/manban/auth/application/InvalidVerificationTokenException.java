package org.mwolff.manban.auth.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Verifikations-Token ist unbekannt, bereits eingelöst oder abgelaufen. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidVerificationTokenException extends RuntimeException
    implements UebersetzbareMeldung {

  public InvalidVerificationTokenException() {
    super("Verifikations-Token ist ungültig oder abgelaufen");
  }

  @Override
  public String schluessel() {
    return "auth.verifikations-token-ungueltig";
  }
}
