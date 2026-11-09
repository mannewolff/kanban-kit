package org.mwolff.manban.benachrichtigung.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Wann eine Mail nach der Ruhezeit fällig ist (Issue #2073, Plan #2070 E7). */
class RuhezeitTest {

  private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

  private static final Ruhezeit MITTAGS =
      new Ruhezeit(LocalTime.of(13, 0), LocalTime.of(15, 0), BERLIN);
  private static final Ruhezeit NACHTS =
      new Ruhezeit(LocalTime.of(22, 0), LocalTime.of(7, 0), BERLIN);
  private static final Ruhezeit BIS_HALB_DREI =
      new Ruhezeit(LocalTime.of(22, 0), LocalTime.of(2, 30), BERLIN);

  @Test
  @DisplayName("Ein Zeitpunkt in der Ruhezeit ergibt ihr Ende")
  void zeitpunktInDerRuhezeit() {
    Instant vierzehnUhr = Instant.parse("2026-11-09T13:00:00Z");

    assertThat(MITTAGS.faelligAb(vierzehnUhr)).isEqualTo(Instant.parse("2026-11-09T14:00:00Z"));
  }

  @Test
  @DisplayName("Ein Zeitpunkt vor oder nach der Ruhezeit ergibt sich selbst")
  void zeitpunktAusserhalb() {
    Instant zwoelfUhr = Instant.parse("2026-11-09T11:00:00Z");
    Instant sechzehnUhr = Instant.parse("2026-11-09T15:00:00Z");

    assertThat(MITTAGS.faelligAb(zwoelfUhr)).isEqualTo(zwoelfUhr);
    assertThat(MITTAGS.faelligAb(sechzehnUhr)).isEqualTo(sechzehnUhr);
  }

  @Test
  @DisplayName("Der Beginn gehört zur Ruhezeit, das Ende nicht")
  void grenzen() {
    Instant beginn = Instant.parse("2026-11-09T12:00:00Z");
    Instant ende = Instant.parse("2026-11-09T14:00:00Z");

    assertThat(MITTAGS.faelligAb(beginn)).isEqualTo(ende);
    assertThat(MITTAGS.faelligAb(ende)).isEqualTo(ende);
  }

  @Test
  @DisplayName("Über Mitternacht: vor Mitternacht ergibt das Ende am Folgetag")
  void vorMitternacht() {
    Instant dreiundzwanzigUhr = Instant.parse("2026-11-09T22:00:00Z");

    assertThat(NACHTS.faelligAb(dreiundzwanzigUhr))
        .isEqualTo(Instant.parse("2026-11-10T06:00:00Z"));
  }

  @Test
  @DisplayName("Über Mitternacht: nach Mitternacht ergibt das Ende am selben Tag")
  void nachMitternacht() {
    Instant dreiUhr = Instant.parse("2026-11-10T02:00:00Z");

    assertThat(NACHTS.faelligAb(dreiUhr)).isEqualTo(Instant.parse("2026-11-10T06:00:00Z"));
  }

  @Test
  @DisplayName("Über Mitternacht: zwischen Ende und Beginn ergibt sich der Zeitpunkt selbst")
  void tagsueberBeiNaechtlicherRuhezeit() {
    Instant zwoelfUhr = Instant.parse("2026-11-10T11:00:00Z");

    assertThat(NACHTS.faelligAb(zwoelfUhr)).isEqualTo(zwoelfUhr);
  }

  @Test
  @DisplayName("Sind Beginn und Ende gleich, gibt es keine Ruhezeit")
  void leereRuhezeit() {
    Ruhezeit leer = new Ruhezeit(LocalTime.of(8, 0), LocalTime.of(8, 0), BERLIN);
    Instant achtUhr = Instant.parse("2026-11-09T07:00:00Z");

    assertThat(leer.faelligAb(achtUhr)).isEqualTo(achtUhr);
  }

  @Test
  @DisplayName("Gerechnet wird in der gespeicherten Zone, nicht in der von Berlin")
  void gespeicherteZone() {
    Ruhezeit newYork =
        new Ruhezeit(LocalTime.of(22, 0), LocalTime.of(7, 0), ZoneId.of("America/New_York"));
    Instant dreiundzwanzigUhrDort = Instant.parse("2026-11-10T04:00:00Z");

    assertThat(newYork.faelligAb(dreiundzwanzigUhrDort))
        .isEqualTo(Instant.parse("2026-11-10T12:00:00Z"));
  }

  @Test
  @DisplayName("Zeitumstellung im Frühjahr: das Ende liegt schon in der Sommerzeit")
  void endeAmTagDerUmstellungImFruehjahr() {
    Instant dreiundzwanzigUhr = Instant.parse("2026-03-28T22:00:00Z");

    assertThat(NACHTS.faelligAb(dreiundzwanzigUhr))
        .isEqualTo(Instant.parse("2026-03-29T05:00:00Z"));
  }

  @Test
  @DisplayName("Zeitumstellung im Herbst: das Ende liegt schon in der Winterzeit")
  void endeAmTagDerUmstellungImHerbst() {
    Instant dreiundzwanzigUhr = Instant.parse("2026-10-24T21:00:00Z");

    assertThat(NACHTS.faelligAb(dreiundzwanzigUhr))
        .isEqualTo(Instant.parse("2026-10-25T06:00:00Z"));
  }

  @Test
  @DisplayName("Fällt das Ende in die übersprungene Stunde, endet die Ruhezeit nach dem Sprung")
  void endeInDerUebersprungenenStunde() {
    Instant dreiundzwanzigUhr = Instant.parse("2026-03-28T22:00:00Z");
    Instant zehnNachDrei = Instant.parse("2026-03-29T01:10:00Z");

    assertThat(BIS_HALB_DREI.faelligAb(dreiundzwanzigUhr))
        .isEqualTo(Instant.parse("2026-03-29T01:00:00Z"));
    assertThat(BIS_HALB_DREI.faelligAb(zehnNachDrei)).isEqualTo(zehnNachDrei);
  }

  @Test
  @DisplayName("Fällt das Ende in die doppelte Stunde, gilt sein erstes Auftreten")
  void endeInDerDoppeltenStunde() {
    Instant dreiundzwanzigUhr = Instant.parse("2026-10-24T21:00:00Z");

    assertThat(BIS_HALB_DREI.faelligAb(dreiundzwanzigUhr))
        .isEqualTo(Instant.parse("2026-10-25T00:30:00Z"));
  }

  @Test
  @DisplayName("Im zweiten Durchlauf der doppelten Stunde liegt das Ende schon zurück")
  void zweiterDurchlaufDerDoppeltenStunde() {
    Instant zehnNachZweiWinterzeit = Instant.parse("2026-10-25T01:10:00Z");

    assertThat(BIS_HALB_DREI.faelligAb(zehnNachZweiWinterzeit)).isEqualTo(zehnNachZweiWinterzeit);
  }
}
