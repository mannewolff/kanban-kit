package org.mwolff.manban.auth.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Ein Admin kann sein eigenes Konto nicht sperren (400). */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class CannotDisableSelfException extends RuntimeException implements UebersetzbareMeldung {

  public CannotDisableSelfException() {
    super("Das eigene Konto kann nicht gesperrt werden");
  }

  @Override
  public String schluessel() {
    return "auth.eigenes-konto-nicht-sperrbar";
  }
}
