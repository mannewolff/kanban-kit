package org.mwolff.manban.common;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalInt;

/**
 * Zwölf rollende 7-Tage-Fenster bis „jetzt“ (Issue #1753, Plan #1752 E6): Index 0 ist das älteste,
 * Index {@code ANZAHL - 1} das jüngste Fenster. Die Platten „Durchsatz“ und „Nacharbeit“ teilen
 * diese Rechnung, damit ihre Fenster nicht auseinanderlaufen.
 */
public final class Wochenfenster {

  /** Zahl der Fenster. */
  public static final int ANZAHL = 12;

  private static final long WOCHE_SEKUNDEN = Duration.ofDays(7).toSeconds();

  private Wochenfenster() {}

  /**
   * Fenster, in das {@code zeitpunkt} fällt; leer, wenn er nach {@code jetzt} oder vor dem ältesten
   * Fenster liegt.
   */
  public static OptionalInt index(Instant zeitpunkt, Instant jetzt) {
    long sekundenZurueck = Duration.between(zeitpunkt, jetzt).toSeconds();
    long wochenZurueck = sekundenZurueck / WOCHE_SEKUNDEN;
    if (sekundenZurueck >= 0 && wochenZurueck < ANZAHL) {
      return OptionalInt.of((int) (ANZAHL - 1 - wochenZurueck));
    }
    return OptionalInt.empty();
  }

  /** Beginn des Fensters {@code index}. */
  public static Instant beginn(int index, Instant jetzt) {
    return jetzt.minusSeconds((ANZAHL - index) * WOCHE_SEKUNDEN);
  }
}
