package org.mwolff.manban.push.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der Endpunkt gehört zum laufenden Abo eines anderen Kontos (Issue #1997). Übernommen wird er
 * nicht: Der Browser legt nach dem Abmelden ein neues Abo mit neuem Endpunkt an.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class PushAboVergebenException extends RuntimeException implements UebersetzbareMeldung {

  private static final long serialVersionUID = 1L;

  public PushAboVergebenException() {
    super(
        "Dieser Endpunkt ist für ein anderes Konto abonniert."
            + " Bitte das Abo auf dem Gerät neu anlegen.");
  }

  @Override
  public String schluessel() {
    return "push.abo.vergeben";
  }
}
