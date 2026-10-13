package org.mwolff.manban.auth.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Benutzer existiert nicht. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class UserNotFoundException extends RuntimeException implements UebersetzbareMeldung {

  public UserNotFoundException() {
    super("Benutzer nicht gefunden");
  }

  @Override
  public String schluessel() {
    return "auth.benutzer-nicht-gefunden";
  }
}
