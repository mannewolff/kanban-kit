package org.mwolff.manban.auth.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * In dieser Anmeldung läuft schon eine Vorschau „Ansicht als XY“; wer eine andere will, beendet die
 * laufende zuerst (Issue #1917, Plan #1914 E11).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class VorschauLaeuftBereitsException extends RuntimeException
    implements UebersetzbareMeldung {

  public VorschauLaeuftBereitsException() {
    super("Es läuft bereits eine Vorschau. Bitte beende sie zuerst.");
  }

  @Override
  public String schluessel() {
    return "auth.vorschau-laeuft-bereits";
  }
}
