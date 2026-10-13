package org.mwolff.manban.auth.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Das Konto wurde von einem Admin gesperrt (403). */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class UserDisabledException extends RuntimeException implements UebersetzbareMeldung {

  public UserDisabledException() {
    super("Konto gesperrt");
  }

  @Override
  public String schluessel() {
    return "auth.konto-gesperrt";
  }
}
