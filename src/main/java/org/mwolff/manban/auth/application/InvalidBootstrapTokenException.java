package org.mwolff.manban.auth.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Bootstrap-Token fehlt in der Konfiguration oder stimmt nicht überein. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class InvalidBootstrapTokenException extends RuntimeException
    implements UebersetzbareMeldung {

  public InvalidBootstrapTokenException() {
    super("Ungültiger oder nicht konfigurierter Bootstrap-Token");
  }

  @Override
  public String schluessel() {
    return "auth.bootstrap-token-ungueltig";
  }
}
