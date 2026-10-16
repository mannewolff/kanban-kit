package org.mwolff.manban.auth.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Grenze je Adresse für Code-Anforderungen ist erreicht (429, Plan #1801 A6). Der Hinweis ist
 * fest und nennt die Adresse nicht: Er erscheint für registrierte und unbekannte Adressen gleich.
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class LoginCodeLimitExceededException extends RuntimeException
    implements UebersetzbareMeldung {

  public LoginCodeLimitExceededException() {
    super("Zu viele Code-Anforderungen für diese Adresse. Bitte versuche es später noch einmal.");
  }

  @Override
  public String schluessel() {
    return "auth.anmeldecode-grenze";
  }
}
