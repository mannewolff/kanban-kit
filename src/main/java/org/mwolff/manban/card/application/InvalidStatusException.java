package org.mwolff.manban.card.application;

import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Ein Status lässt sich so nicht setzen (Plan #1294, E9): Der Wert ist keiner der fünf
 * Prozesszustände, oder die Karte trägt keinen eigenen Status (Vorhaben, Dokumentart).
 *
 * <p>Je Fall eine Fabrikmethode mit eigenem Meldungsschlüssel (Issue #1642).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public final class InvalidStatusException extends RuntimeException implements UebersetzbareMeldung {

  private final String meldungsschluessel;

  private InvalidStatusException(String schluessel, String text, @Nullable Throwable ursache) {
    super(text, ursache);
    this.meldungsschluessel = schluessel;
  }

  /** Die Karte trägt keinen eigenen Status (Vorhaben, Dokumentart). */
  public static InvalidStatusException keinEigener() {
    return new InvalidStatusException(
        "card.status.kein-eigener", "Diese Karte trägt keinen eigenen Status", null);
  }

  /**
   * Der Wert ist keiner der fünf Prozesszustände; die Meldung wiederholt die Eingabe nicht, sie
   * stammt vom Client.
   */
  public static InvalidStatusException unbekannt(Throwable ursache) {
    return new InvalidStatusException("card.status.unbekannt", "Unbekannter Status", ursache);
  }

  @Override
  public String schluessel() {
    return meldungsschluessel;
  }
}
