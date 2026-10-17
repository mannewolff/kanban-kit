package org.mwolff.manban.card.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tagesgrenze 12:00 bis 12:00 der Leitstand-Prognose (Issue #1823, Plan #1819, AK 15). */
class PrognosetagTest {

  private static final ZoneId ZONE_BERLIN = ZoneId.of("Europe/Berlin");
  private static final ZoneId ZONE_NEW_YORK = ZoneId.of("America/New_York");
  private static final ZoneId ZONE_TOKIO = ZoneId.of("Asia/Tokyo");

  private static Instant berlin(String lokal) {
    return ZonedDateTime.of(LocalDateTime.parse(lokal), ZONE_BERLIN).toInstant();
  }

  private static Duration laenge(LocalDate tag, ZoneId zone) {
    return Duration.between(
        Prognosetag.beginn(tag, zone), Prognosetag.beginn(tag.plusDays(1), zone));
  }

  @Test
  @DisplayName("11:59 gehört noch zum Tag, der heute um 12:00 endet")
  void elfNeunundfuenfzigGehoertZumHeutigenTag() {
    assertThat(Prognosetag.laufenderTag(berlin("2026-10-17T11:59"), ZONE_BERLIN))
        .isEqualTo(LocalDate.parse("2026-10-17"));
  }

  @Test
  @DisplayName("12:00 beginnt den Tag, der morgen um 12:00 endet")
  void zwoelfUhrBeginntDenNaechstenTag() {
    assertThat(Prognosetag.laufenderTag(berlin("2026-10-17T12:00"), ZONE_BERLIN))
        .isEqualTo(LocalDate.parse("2026-10-18"));
  }

  @Test
  @DisplayName("Der Tag trägt das Datum seines Endes und beginnt am Vortag um 12:00")
  void benennungNachDemEnde() {
    LocalDate tag = LocalDate.parse("2026-10-18");

    assertThat(Prognosetag.beginn(tag, ZONE_BERLIN)).isEqualTo(berlin("2026-10-17T12:00"));
    assertThat(Prognosetag.tagDes(berlin("2026-10-17T23:30"), ZONE_BERLIN)).isEqualTo(tag);
    assertThat(Prognosetag.tagDes(berlin("2026-10-18T08:00"), ZONE_BERLIN)).isEqualTo(tag);
  }

  @Test
  @DisplayName("Der Beginn zählt zum Tag, der Augenblick davor zum Vortag")
  void beginnGehoertZumTag() {
    LocalDate tag = LocalDate.parse("2026-10-18");
    Instant beginn = Prognosetag.beginn(tag, ZONE_BERLIN);

    assertThat(Prognosetag.tagDes(beginn, ZONE_BERLIN)).isEqualTo(tag);
    assertThat(Prognosetag.tagDes(beginn.minusNanos(1), ZONE_BERLIN)).isEqualTo(tag.minusDays(1));
  }

  @Test
  @DisplayName("Der Tag mit der Umstellung auf Sommerzeit hat 23 Stunden")
  void sommerzeitTagHatDreiundzwanzigStunden() {
    assertThat(laenge(LocalDate.parse("2026-03-29"), ZONE_BERLIN)).isEqualTo(Duration.ofHours(23));
    assertThat(laenge(LocalDate.parse("2026-03-30"), ZONE_BERLIN)).isEqualTo(Duration.ofHours(24));
  }

  @Test
  @DisplayName("Der Tag mit der Umstellung auf Winterzeit hat 25 Stunden")
  void winterzeitTagHatFuenfundzwanzigStunden() {
    LocalDate tag = LocalDate.parse("2026-10-25");

    assertThat(laenge(tag, ZONE_BERLIN)).isEqualTo(Duration.ofHours(25));
    assertThat(Prognosetag.tagDes(Instant.parse("2026-10-25T00:30:00Z"), ZONE_BERLIN))
        .isEqualTo(tag);
    assertThat(Prognosetag.tagDes(Instant.parse("2026-10-25T01:30:00Z"), ZONE_BERLIN))
        .isEqualTo(tag);
  }

  @Test
  @DisplayName("Zum selben Augenblick laufen in zwei Zonen verschiedene Tage")
  void zweiZonenZweiTage() {
    Instant jetzt = Instant.parse("2026-10-17T11:00:00Z");

    assertThat(Prognosetag.laufenderTag(jetzt, ZONE_BERLIN))
        .isEqualTo(LocalDate.parse("2026-10-18"));
    assertThat(Prognosetag.laufenderTag(jetzt, ZONE_NEW_YORK))
        .isEqualTo(LocalDate.parse("2026-10-17"));
    assertThat(Prognosetag.laufenderTag(jetzt, ZONE_TOKIO))
        .isEqualTo(LocalDate.parse("2026-10-18"));
  }

  @Test
  @DisplayName("Der laufende Tag ist der Tag des Augenblicks jetzt")
  void laufenderTagIstTagDesJetzt() {
    Instant jetzt = Instant.parse("2026-10-25T01:30:00Z");

    assertThat(Prognosetag.laufenderTag(jetzt, ZONE_BERLIN))
        .isEqualTo(Prognosetag.tagDes(jetzt, ZONE_BERLIN));
  }
}
