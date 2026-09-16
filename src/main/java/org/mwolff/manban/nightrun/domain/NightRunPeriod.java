package org.mwolff.manban.nightrun.domain;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * Ein abgeschlossener Auswertungszeitraum der Nachtlauf-Auswertung (Issue #934, Plan #933).
 *
 * <p><b>Die Tagesgrenze liegt bei 12:00 zonenlokal</b> (Issue #969): Ein Lauf, der vor 12:00
 * startet, zählt zur vorangegangenen Nacht, und eine Nacht trägt das Datum ihres Beginns. Mit einer
 * Grenze um 0:00 lägen zwei Läufe derselben Nacht — einer um 23:10, einer um 03:22 — an zwei Tagen.
 * Die Grenze gilt für alle drei Arten: Sonst läge die Nacht vom Sonntag auf den Montag in zwei
 * Wochen und die vom 31. auf den 1. in zwei Monaten.
 *
 * <p>Die Spanne reicht von {@code from} einschließlich bis {@code to} ausschließlich. Die Grenzen
 * werden <b>zonenlokal</b> gezogen — 12:00 am ersten Tag und am Tag nach dem letzten — und erst
 * dann in {@link Instant} übersetzt. Ein Sommerzeitwechsel innerhalb der Spanne verschiebt sie
 * deshalb nicht: Die Nacht über die Umstellung im März hat 23 Stunden, die im Oktober 25.
 *
 * <p><b>Rückschritt 0 ist der zuletzt abgeschlossene Zeitraum</b>, nie der laufende (Plan #933
 * E14): Ein laufender Zeitraum verglichen mit einem abgeschlossenen sähe stets nach Rückgang aus.
 *
 * @param type Art des Zeitraums
 * @param zone Zone, in der die Grenzen gezogen wurden
 * @param firstDay Tag, an dem die erste Nacht des Zeitraums beginnt — die Grundlage der
 *     Beschriftung
 * @param from Beginn, einschließlich
 * @param to Ende, ausschließlich
 */
public record NightRunPeriod(
    NightRunPeriodType type, ZoneId zone, LocalDate firstDay, Instant from, Instant to) {

  /** Die Tagesgrenze einer Nacht (Issue #969): Wer vor ihr startet, gehört zur Nacht davor. */
  public static final LocalTime TAGESGRENZE = LocalTime.NOON;

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
    LocalDate laufendeNacht = nachtDatum(LocalDateTime.ofInstant(clock.instant(), zone));
    LocalDate laufenderBeginn = beginnDesLaufenden(type, laufendeNacht);
    return beginnendAm(type, zone, verschoben(type, laufenderBeginn, -(rueckschritt + 1L)));
  }

  /**
   * Die einzelne Nacht, die am angegebenen Datum beginnt — 12:00 an diesem Tag bis 12:00 am
   * Folgetag (Issue #938). Anders als {@link #of} ohne Bezug auf „jetzt": Die Nacht wird über ihr
   * Datum adressiert (Plan #933 E21).
   */
  public static NightRunPeriod night(LocalDate datum, ZoneId zone) {
    return beginnendAm(NightRunPeriodType.DAY, zone, datum);
  }

  /** Der unmittelbar vorangegangene gleichartige Zeitraum. */
  public NightRunPeriod previous() {
    return beginnendAm(type, zone, verschoben(type, firstDay, -1));
  }

  /**
   * Tag, an dem die letzte Nacht des Zeitraums beginnt, einschließlich — die zweite Grundlage der
   * Beschriftung.
   */
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
        type, zone, beginn, grenze(beginn, zone), grenze(verschoben(type, beginn, 1), zone));
  }

  /** Datum der Nacht, in der ein zonenlokaler Zeitpunkt liegt. */
  private static LocalDate nachtDatum(LocalDateTime lokal) {
    return lokal.toLocalTime().isBefore(TAGESGRENZE)
        ? lokal.toLocalDate().minusDays(1)
        : lokal.toLocalDate();
  }

  /** Beginn der Nacht dieses Datums als Zeitpunkt. */
  private static Instant grenze(LocalDate tag, ZoneId zone) {
    return tag.atTime(TAGESGRENZE).atZone(zone).toInstant();
  }

  private static LocalDate beginnDesLaufenden(NightRunPeriodType type, LocalDate nacht) {
    return switch (type) {
      case DAY -> nacht;
      case WEEK -> nacht.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
      case MONTH -> nacht.withDayOfMonth(1);
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
