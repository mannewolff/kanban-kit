package org.mwolff.manban.push.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der Endpunkt eines Abos zeigt auf keinen bekannten Push-Dienst (Issue #2290): Der Server baut
 * Verbindungen nur zu den Push-Diensten der Browser auf, nie zu einem frei gewählten Ziel.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PushDienstUnbekanntException extends RuntimeException implements UebersetzbareMeldung {

  private static final long serialVersionUID = 1L;

  public PushDienstUnbekanntException() {
    super("Der Endpunkt gehört zu keinem bekannten Push-Dienst.");
  }

  @Override
  public String schluessel() {
    return "push.abo.dienst-unbekannt";
  }
}
