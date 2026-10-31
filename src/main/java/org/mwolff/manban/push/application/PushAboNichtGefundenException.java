package org.mwolff.manban.push.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Zum Endpunkt gibt es kein laufendes Abo des Aufrufers (Issue #1997). Ein fremdes, ein
 * abgelaufenes und ein unbekanntes Abo antworten gleich — kein Existenz-Leak.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class PushAboNichtGefundenException extends RuntimeException
    implements UebersetzbareMeldung {

  private static final long serialVersionUID = 1L;

  public PushAboNichtGefundenException() {
    super("Dieses Gerät hat kein Abo für Benachrichtigungen.");
  }

  @Override
  public String schluessel() {
    return "push.abo.nicht-gefunden";
  }
}
