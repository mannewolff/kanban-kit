package org.mwolff.manban.codeanalyse.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.OptionalInt;

/**
 * Kalenderwochen in UTC für die Platte „Codeanalyse“ (Issue #2273, Plan #2272 E6; Fachplan #2271 AK
 * 7, AK 15): Eine Woche reicht von Montag 00:00 UTC bis Sonntag, das Fenster umfasst die laufende
 * Woche und die elf davor. Index 0 ist die älteste, Index {@code ANZAHL - 1} die laufende Woche.
 * Anders als {@code common.Wochenfenster} rollt das Fenster nicht mit „jetzt“.
 */
public final class Kalenderwochen {

  /** Zahl der Wochen im Fenster. */
  public static final int ANZAHL = 12;

  private static final int TAGE_JE_WOCHE = 7;

  private Kalenderwochen() {}

  /** Montag 00:00 UTC der Woche, in die {@code zeitpunkt} fällt. */
  public static Instant wochenbeginn(Instant zeitpunkt) {
    return LocalDate.ofInstant(zeitpunkt, ZoneOffset.UTC)
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant();
  }

  /** Beginn der ältesten Woche des Fensters bis {@code jetzt}. */
  public static Instant fensterbeginn(Instant jetzt) {
    return beginn(0, jetzt);
  }

  /** Beginn der Woche {@code index} im Fenster bis {@code jetzt}. */
  public static Instant beginn(int index, Instant jetzt) {
    return wochenbeginn(jetzt).minus((long) (ANZAHL - 1 - index) * TAGE_JE_WOCHE, ChronoUnit.DAYS);
  }

  /**
   * Woche, in die {@code zeitpunkt} fällt; leer, wenn er nach {@code jetzt} oder vor dem Fenster
   * liegt.
   */
  public static OptionalInt index(Instant zeitpunkt, Instant jetzt) {
    if (zeitpunkt.isAfter(jetzt) || zeitpunkt.isBefore(fensterbeginn(jetzt))) {
      return OptionalInt.empty();
    }
    long tage = ChronoUnit.DAYS.between(fensterbeginn(jetzt), wochenbeginn(zeitpunkt));
    return OptionalInt.of((int) (tage / TAGE_JE_WOCHE));
  }
}
