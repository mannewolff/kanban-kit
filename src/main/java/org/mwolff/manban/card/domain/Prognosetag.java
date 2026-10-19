package org.mwolff.manban.card.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Der Tag der Leitstand-Prognose (Plan #1819, AK 15): Er reicht von 12:00 bis 12:00 in der Zone des
 * Lesers und trägt das Datum seines Endes. Der Tag „2026-10-18“ beginnt also am 2026-10-17 um 12:00
 * und endet am 2026-10-18 um 12:00. An einer Zeitumstellung hat er 23 oder 25 Stunden. Rein, ohne
 * Datenbank.
 */
public final class Prognosetag {

  private static final int GRENZSTUNDE = 12;

  private Prognosetag() {}

  /** Der Tag, der {@code jetzt} enthält — benannt nach seinem Ende. */
  public static LocalDate laufenderTag(Instant jetzt, ZoneId zone) {
    return tagDes(jetzt, zone);
  }

  /** Der Beginn des Tags: 12:00 am Vortag des Datums, eingeschlossen. */
  public static Instant beginn(LocalDate tag, ZoneId zone) {
    return tag.minusDays(1).atTime(GRENZSTUNDE, 0).atZone(zone).toInstant();
  }

  /** Der Tag, in den {@code zeitpunkt} fällt — benannt nach seinem Ende. */
  public static LocalDate tagDes(Instant zeitpunkt, ZoneId zone) {
    LocalDate datum = zeitpunkt.atZone(zone).toLocalDate();
    LocalDate folgetag = datum.plusDays(1);
    return zeitpunkt.isBefore(beginn(folgetag, zone)) ? datum : folgetag;
  }
}
