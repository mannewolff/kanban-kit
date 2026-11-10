package org.mwolff.manban.entscheidungskette.domain;

import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.KitMeldungen.Wert;

/**
 * Die Meldung einer Übertragung auf den Hauptzweig. Leer ist, was fehlt oder als {@code nicht
 * erfasst} gemeldet wurde.
 *
 * @param versuch Kennung des Versuchs
 * @param commit der übertragene Stand
 * @param basis der Stand davor
 * @param weg einer der Werte {@code LOKAL}, {@code BUILD_DIENST}, {@code NOTFALL}, {@code
 *     VORBEREITET}
 * @param prueflauf Ergebnis und Zeitpunkt des Laufs, der auf diesem Weg zählt
 * @param buildDienst Ergebnis und Zeitpunkt des Build-Diensts
 * @param quelle Verweis auf den Lauf, wörtlich
 * @param ausgang der Ausgang, wörtlich
 * @param freigegebenVon wer freigegeben hat und wann
 * @param ausgefuehrtVon wer ausgeführt hat und wann
 */
public record Uebertragung(
    @Nullable String versuch,
    @Nullable String commit,
    @Nullable String basis,
    @Nullable Wert weg,
    Angabe prueflauf,
    Angabe buildDienst,
    @Nullable String quelle,
    @Nullable String ausgang,
    Angabe freigegebenVon,
    Angabe ausgefuehrtVon)
    implements KitMeldung, Einzelheiten {

  /** Nur eine Übertragung mit Ausgang, der nicht {@code nicht übertragen} lautet, zählt. */
  @Override
  public boolean erreicht() {
    return ausgang != null && !Wert.NICHT_UEBERTRAGEN.bezeichnet(ausgang);
  }
}
