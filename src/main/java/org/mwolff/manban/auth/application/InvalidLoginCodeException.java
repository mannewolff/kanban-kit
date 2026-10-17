package org.mwolff.manban.auth.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Code oder Link der Code-Anmeldung lassen sich nicht einlösen (401, Plan #1801 E6). Falscher,
 * abgelaufener, verbrauchter oder ersetzter Code und unbekannte Adresse bekommen dieselbe Meldung:
 * Unterschiede verrieten Konto oder Zustand.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidLoginCodeException extends RuntimeException implements UebersetzbareMeldung {

  public InvalidLoginCodeException() {
    super("Code ungültig oder abgelaufen — fordere einen neuen an");
  }

  @Override
  public String schluessel() {
    return "auth.anmeldecode-ungueltig";
  }
}
