package org.mwolff.manban.entscheidungskette.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Die Angaben eines Versuchs der Umsetzung (Plan #2108 E10).
 *
 * @param ausgang wie der Versuch endete
 * @param beendetAm wann; {@code null} bei einem offenen Versuch
 * @param commit der Commit aus dem Abschlussbericht; {@code null}, wenn nicht erfasst
 * @param prueflauf Ergebnis und Zeitpunkt des deckenden Prüflaufs
 */
public record Umsetzungsangaben(
    Umsetzungsausgang ausgang,
    @Nullable Instant beendetAm,
    @Nullable String commit,
    Angabe prueflauf)
    implements Einzelheiten {

  /** Ein zurückgezogener Versuch zählt nicht als erreichte Umsetzung. */
  @Override
  public boolean erreicht() {
    return ausgang != Umsetzungsausgang.ZURUECKGEZOGEN;
  }
}
