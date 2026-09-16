package org.mwolff.manban.nightrun.domain;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * Ein abgeschlossener Auswertungszeitraum der Nachtlauf-Auswertung (Issue #934, Plan #933).
 *
 * <p>Die Spanne reicht von {@code from} einschließlich bis {@code to} ausschließlich. Die Grenzen
 * werden <b>zonenlokal</b> gezogen — lokale Mitternacht am ersten Tag und am Tag nach dem letzten —
 * und erst dann in {@link Instant} übersetzt. Ein Sommerzeitwechsel innerhalb der Spanne verschiebt
 * sie deshalb nicht: Der Umstellungstag im März hat 23 Stunden, der im Oktober 25.
 *
 * <p><b>Rückschritt 0 ist der zuletzt abgeschlossene Zeitraum</b>, nie der laufende (Plan #933
 * E14): Ein laufender Zeitraum verglichen mit einem abgeschlossenen sähe stets nach Rückgang aus.
 *
 * @param type Art des Zeitraums
 * @param zone Zone, in der die Grenzen gezogen wurden
 * @param firstDay erster Tag des Zeitraums — die Grundlage der Beschriftung
 * @param from Beginn, einschließlich
 * @param to Ende, ausschließlich
 */
public record NightRunPeriod(
    NightRunPeriodType type, ZoneId zone, LocalDate firstDay, Instant from, Instant to) {

  /**
   * Der Zeitraum der gewählten Art, {@code rueckschritt} Zeiträume vor dem zuletzt abgeschlossenen.
   *
   * @throws IllegalArgumentException bei negativem Rückschritt — ein Blick in die Zukunft ist kein
   *     abgeschlossener Zeitraum
   */
  public static NightRunPeriod of(
      NightRunPeriodType type, ZoneId zone, Clock clock, int rueckschritt) {
    if (rueckschritt < 0) {
      throw new IllegalArgumentException(
          "Der Rückschritt darf nicht negativ sein, war " + rueckschritt);
    }
    LocalDate heute = LocalDate.ofInstant(clock.instant(), zone);
    LocalDate laufenderBeginn = beginnDesLaufenden(type, heute);
    return beginnendAm(type, zone, verschoben(type, laufenderBeginn, -(rueckschritt + 1L)));
  }

  /** Der unmittelbar vorangegangene gleichartige Zeitraum. */
  public NightRunPeriod previous() {
    return beginnendAm(type, zone, verschoben(type, firstDay, -1));
  }

  /** Letzter Tag des Zeitraums, einschließlich — die zweite Grundlage der Beschriftung. */
  public LocalDate lastDay() {
    return verschoben(type, firstDay, 1).minusDays(1);
  }

  /** Ob der Zeitpunkt in der Spanne liegt: Beginn eingeschlossen, Ende ausgeschlossen. */
  public boolean contains(Instant zeitpunkt) {
    return !zeitpunkt.isBefore(from) && zeitpunkt.isBefore(to);
  }

  private static NightRunPeriod beginnendAm(
      NightRunPeriodType type, ZoneId zone, LocalDate beginn) {
    return new NightRunPeriod(
        type,
        zone,
        beginn,
        beginn.atStartOfDay(zone).toInstant(),
        verschoben(type, beginn, 1).atStartOfDay(zone).toInstant());
  }

  private static LocalDate beginnDesLaufenden(NightRunPeriodType type, LocalDate heute) {
    return switch (type) {
      case DAY -> heute;
      case WEEK -> heute.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
      case MONTH -> heute.withDayOfMonth(1);
    };
  }

  private static LocalDate verschoben(NightRunPeriodType type, LocalDate beginn, long anzahl) {
    return switch (type) {
      case DAY -> beginn.plusDays(anzahl);
      case WEEK -> beginn.plusWeeks(anzahl);
      case MONTH -> beginn.plusMonths(anzahl);
    };
  }
}
