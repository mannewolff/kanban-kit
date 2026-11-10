package org.mwolff.manban.benachrichtigung.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;

/**
 * Die Ruhezeit einer Person (Issue #2073, Plan #2070 E7): von {@code beginn} einschließlich bis
 * {@code ende} ausschließlich, gelesen auf der Uhr der Zone {@code zone}.
 *
 * <p>Liegt {@code beginn} nach {@code ende}, reicht die Ruhezeit über Mitternacht. Sind beide
 * gleich, gibt es keine Ruhezeit.
 */
public record Ruhezeit(LocalTime beginn, LocalTime ende, ZoneId zone) {

  /**
   * Ab wann eine Mail zu diesem Zeitpunkt fällig ist.
   *
   * <p>Am Tag einer Zeitumstellung gilt: Fällt das Ende in die übersprungene Stunde, endet die
   * Ruhezeit mit dem Sprung; fällt es in die doppelte Stunde, gilt sein erstes Auftreten. Ein
   * Zeitpunkt im zweiten Durchlauf liegt danach und ist sofort fällig.
   *
   * @return das Ende der Ruhezeit, liegt der Zeitpunkt außerhalb, der Zeitpunkt selbst
   */
  public Instant faelligAb(Instant zeitpunkt) {
    LocalDateTime ortszeit = LocalDateTime.ofInstant(zeitpunkt, zone);
    LocalTime uhrzeit = ortszeit.toLocalTime();
    boolean nachBeginn = !uhrzeit.isBefore(beginn);
    boolean vorEnde = uhrzeit.isBefore(ende);
    boolean ueberMitternacht = beginn.isAfter(ende);

    // Mutations-Altlast: äquivalent, vergangenes Ende ergibt den Zeitpunkt (#2095, 2026-11-10)
    if (!(ueberMitternacht ? nachBeginn || vorEnde : nachBeginn && vorEnde)) {
      return zeitpunkt;
    }
    LocalDate tag = ortszeit.toLocalDate();
    Instant endeDerRuhezeit = endeAm(ueberMitternacht && nachBeginn ? tag.plusDays(1) : tag);
    return endeDerRuhezeit.isAfter(zeitpunkt) ? endeDerRuhezeit : zeitpunkt;
  }

  private Instant endeAm(LocalDate tag) {
    LocalDateTime wanduhr = tag.atTime(ende);
    ZoneOffsetTransition umstellung = zone.getRules().getTransition(wanduhr);
    if (umstellung != null && umstellung.isGap()) {
      wanduhr = umstellung.getDateTimeAfter();
    }
    return wanduhr.atZone(zone).toInstant();
  }
}
