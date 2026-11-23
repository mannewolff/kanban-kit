package org.mwolff.manban.comment.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Freigabe-Rückfrage ist überholt: Für das Projekt ist eine jüngere Vorschau mit anderem Stand
 * gemeldet, ihre Freigabe gäbe nicht mehr frei, was gezeigt wurde (Issue #2210, Plan #2206 E11).
 * Ergibt HTTP 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class RueckfrageUeberholtException extends RuntimeException implements UebersetzbareMeldung {

  public RueckfrageUeberholtException() {
    super("Es gibt eine neuere Vorschau; diese Freigabe ist überholt");
  }

  @Override
  public String schluessel() {
    return "comment.rueckfrage.ueberholt";
  }
}
