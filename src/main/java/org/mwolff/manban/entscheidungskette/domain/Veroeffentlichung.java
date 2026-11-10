package org.mwolff.manban.entscheidungskette.domain;

import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.KitMeldungen.Wert;

/**
 * Die Meldung einer Veröffentlichung. Leer ist, was fehlt oder als {@code nicht erfasst} gemeldet
 * wurde.
 *
 * @param versuch Kennung des Versuchs
 * @param version die Version
 * @param commit der veröffentlichte Stand
 * @param basis der Stand davor
 * @param ausgang der Ausgang, wörtlich
 * @param quelle Verweis auf den Pull Request, wörtlich
 * @param freigegebenVon wer freigegeben hat und wann
 * @param ausgefuehrtVon wer ausgeführt hat und wann
 */
public record Veroeffentlichung(
    @Nullable String versuch,
    @Nullable String version,
    @Nullable String commit,
    @Nullable String basis,
    @Nullable String ausgang,
    @Nullable String quelle,
    Angabe freigegebenVon,
    Angabe ausgefuehrtVon)
    implements KitMeldung, Einzelheiten {

  /**
   * Ob der Ausgang eine Veröffentlichung ist (Plan #2108 E15): {@code Pull Request angelegt} und
   * {@code nicht veröffentlicht} sind es nicht, ein fehlender Ausgang auch nicht, jeder andere
   * schon.
   */
  public boolean veroeffentlicht() {
    return ausgang != null
        && !Wert.PULL_REQUEST_ANGELEGT.bezeichnet(ausgang)
        && !Wert.NICHT_VEROEFFENTLICHT.bezeichnet(ausgang);
  }

  /** Nur eine Veröffentlichung zählt als erreicht. */
  @Override
  public boolean erreicht() {
    return veroeffentlicht();
  }
}
