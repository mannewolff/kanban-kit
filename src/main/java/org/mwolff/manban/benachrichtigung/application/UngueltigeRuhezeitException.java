package org.mwolff.manban.benachrichtigung.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Ruhezeit lässt sich nicht speichern (Issue #2076): Beginn und Ende sind gleich, was still
 * jede Mail verhinderte oder keine Ruhezeit wäre, oder die Zone steht nicht in der IANA-Liste.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public final class UngueltigeRuhezeitException extends RuntimeException
    implements UebersetzbareMeldung {

  private static final long serialVersionUID = 1L;

  private final String textSchluessel;

  private UngueltigeRuhezeitException(String schluessel, String meldung) {
    super(meldung);
    this.textSchluessel = schluessel;
  }

  static UngueltigeRuhezeitException gleicheZeiten() {
    return new UngueltigeRuhezeitException(
        "benachrichtigung.ruhezeit.gleiche-zeiten",
        "Beginn und Ende der Ruhezeit dürfen nicht gleich sein.");
  }

  static UngueltigeRuhezeitException unbekannteZone() {
    return new UngueltigeRuhezeitException(
        "benachrichtigung.ruhezeit.unbekannte-zone", "Diese Zeitzone ist nicht bekannt.");
  }

  @Override
  public String schluessel() {
    return textSchluessel;
  }
}
