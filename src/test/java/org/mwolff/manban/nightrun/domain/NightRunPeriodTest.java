package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Zeitraumgrenzen der Verbrauchs-Auswertung (Issue #934, Plan #933 E14, E18, E19). */
class NightRunPeriodTest {

  private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

  private static Clock am(String instant) {
    return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
  }

  // --- Tag ---------------------------------------------------------------------------------

  @Test
  void rueckschrittNullIstDerZuletztAbgeschlosseneTag_nichtDerLaufende() {
    NightRunPeriod tag =
        NightRunPeriod.of(NightRunPeriodType.DAY, BERLIN, am("2026-09-16T10:00:00Z"), 0);

    assertThat(tag.type()).isEqualTo(NightRunPeriodType.DAY);
    assertThat(tag.firstDay()).isEqualTo(LocalDate.of(2026, 9, 15));
    assertThat(tag.lastDay()).isEqualTo(LocalDate.of(2026, 9, 15));
    assertThat(tag.from()).isEqualTo(Instant.parse("2026-09-14T22:00:00Z"));
    assertThat(tag.to()).isEqualTo(Instant.parse("2026-09-15T22:00:00Z"));
  }

  @Test
  void rueckschrittEinsIstDerTagDavor() {
    NightRunPeriod tag =
        NightRunPeriod.of(NightRunPeriodType.DAY, BERLIN, am("2026-09-16T10:00:00Z"), 1);

    assertThat(tag.firstDay()).isEqualTo(LocalDate.of(2026, 9, 14));
  }

  /** Der Tag wird zonenlokal bestimmt: 23:30 UTC ist in Berlin schon der naechste Tag. */
  @Test
  void derTagFolgtDerZone_nichtUtc() {
    NightRunPeriod tag =
        NightRunPeriod.of(NightRunPeriodType.DAY, BERLIN, am("2026-09-15T23:30:00Z"), 0);

    assertThat(tag.firstDay()).isEqualTo(LocalDate.of(2026, 9, 15));
  }

  /** Der Umstellungstag im Maerz hat 23 Stunden; die Grenzen bleiben lokale Mitternacht. */
  @Test
  void derUmstellungstagImMaerzHatDreiundzwanzigStunden() {
    NightRunPeriod tag =
        NightRunPeriod.of(NightRunPeriodType.DAY, BERLIN, am("2026-03-30T10:00:00Z"), 0);

    assertThat(tag.firstDay()).isEqualTo(LocalDate.of(2026, 3, 29));
    assertThat(tag.from()).isEqualTo(Instant.parse("2026-03-28T23:00:00Z"));
    assertThat(tag.to()).isEqualTo(Instant.parse("2026-03-29T22:00:00Z"));
    assertThat(Duration.between(tag.from(), tag.to())).isEqualTo(Duration.ofHours(23));
  }

  @Test
  void derUmstellungstagImOktoberHatFuenfundzwanzigStunden() {
    NightRunPeriod tag =
        NightRunPeriod.of(NightRunPeriodType.DAY, BERLIN, am("2026-10-26T10:00:00Z"), 0);

    assertThat(tag.firstDay()).isEqualTo(LocalDate.of(2026, 10, 25));
    assertThat(tag.from()).isEqualTo(Instant.parse("2026-10-24T22:00:00Z"));
    assertThat(tag.to()).isEqualTo(Instant.parse("2026-10-25T23:00:00Z"));
    assertThat(Duration.between(tag.from(), tag.to())).isEqualTo(Duration.ofHours(25));
  }

  // --- Woche -------------------------------------------------------------------------------

  /**
   * 16.09.2026 ist ein Mittwoch; die zuletzt abgeschlossene Woche lief von Montag 07. bis 13.09.
   */
  @Test
  void dieWocheBeginntAmMontag_undRueckschrittNullIstDieZuletztAbgeschlossene() {
    NightRunPeriod woche =
        NightRunPeriod.of(NightRunPeriodType.WEEK, BERLIN, am("2026-09-16T10:00:00Z"), 0);

    assertThat(woche.firstDay()).isEqualTo(LocalDate.of(2026, 9, 7));
    assertThat(woche.firstDay().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    assertThat(woche.lastDay()).isEqualTo(LocalDate.of(2026, 9, 13));
    assertThat(woche.from()).isEqualTo(Instant.parse("2026-09-06T22:00:00Z"));
    assertThat(woche.to()).isEqualTo(Instant.parse("2026-09-13T22:00:00Z"));
  }

  /** Am Montag selbst laeuft die neue Woche; abgeschlossen ist die davor. */
  @Test
  void amMontagIstDieVorwocheDieZuletztAbgeschlossene() {
    NightRunPeriod woche =
        NightRunPeriod.of(NightRunPeriodType.WEEK, BERLIN, am("2026-09-14T08:00:00Z"), 0);

    assertThat(woche.firstDay()).isEqualTo(LocalDate.of(2026, 9, 7));
  }

  @Test
  void eineWocheUeberDenUmstellungstagImOktoberHatEineStundeMehr() {
    NightRunPeriod woche =
        NightRunPeriod.of(NightRunPeriodType.WEEK, BERLIN, am("2026-10-28T10:00:00Z"), 0);

    assertThat(woche.firstDay()).isEqualTo(LocalDate.of(2026, 10, 19));
    assertThat(woche.from()).isEqualTo(Instant.parse("2026-10-18T22:00:00Z"));
    assertThat(woche.to()).isEqualTo(Instant.parse("2026-10-25T23:00:00Z"));
  }

  @Test
  void rueckschrittZweiIstDieWocheVorDerVorwoche() {
    NightRunPeriod woche =
        NightRunPeriod.of(NightRunPeriodType.WEEK, BERLIN, am("2026-09-16T10:00:00Z"), 2);

    assertThat(woche.firstDay()).isEqualTo(LocalDate.of(2026, 8, 24));
  }

  // --- Monat -------------------------------------------------------------------------------

  @Test
  void rueckschrittNullIstDerZuletztAbgeschlosseneMonat() {
    NightRunPeriod monat =
        NightRunPeriod.of(NightRunPeriodType.MONTH, BERLIN, am("2026-09-16T10:00:00Z"), 0);

    assertThat(monat.firstDay()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(monat.lastDay()).isEqualTo(LocalDate.of(2026, 8, 31));
    assertThat(monat.from()).isEqualTo(Instant.parse("2026-07-31T22:00:00Z"));
    assertThat(monat.to()).isEqualTo(Instant.parse("2026-08-31T22:00:00Z"));
  }

  /** Der Maerz traegt die Umstellung: Beginn in Winterzeit (+01), Ende in Sommerzeit (+02). */
  @Test
  void derMaerzBeginntInWinterzeitUndEndetInSommerzeit() {
    NightRunPeriod maerz =
        NightRunPeriod.of(NightRunPeriodType.MONTH, BERLIN, am("2026-04-10T10:00:00Z"), 0);

    assertThat(maerz.firstDay()).isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(maerz.from()).isEqualTo(Instant.parse("2026-02-28T23:00:00Z"));
    assertThat(maerz.to()).isEqualTo(Instant.parse("2026-03-31T22:00:00Z"));
  }

  @Test
  void rueckschrittUeberDenJahreswechsel() {
    NightRunPeriod monat =
        NightRunPeriod.of(NightRunPeriodType.MONTH, BERLIN, am("2026-02-10T10:00:00Z"), 1);

    assertThat(monat.firstDay()).isEqualTo(LocalDate.of(2025, 12, 1));
    assertThat(monat.lastDay()).isEqualTo(LocalDate.of(2025, 12, 31));
  }

  // --- Vorzeitraum und Eingaben ------------------------------------------------------------

  @Test
  void derVorzeitraumIstDerUnmittelbarVorangegangeneGleichartige() {
    Clock jetzt = am("2026-09-16T10:00:00Z");
    for (NightRunPeriodType art : NightRunPeriodType.values()) {
      NightRunPeriod zeitraum = NightRunPeriod.of(art, BERLIN, jetzt, 0);

      NightRunPeriod vorher = zeitraum.previous();

      assertThat(vorher).isEqualTo(NightRunPeriod.of(art, BERLIN, jetzt, 1));
      assertThat(vorher.to()).isEqualTo(zeitraum.from());
    }
  }

  @Test
  void einNegativerRueckschrittWirdAbgewiesen() {
    Clock jetzt = am("2026-09-16T10:00:00Z");

    assertThatThrownBy(() -> NightRunPeriod.of(NightRunPeriodType.DAY, BERLIN, jetzt, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("-1");
  }

  @Test
  void rueckschrittNullIstErlaubt() {
    Clock jetzt = am("2026-09-16T10:00:00Z");

    assertThat(NightRunPeriod.of(NightRunPeriodType.DAY, BERLIN, jetzt, 0).zone())
        .isEqualTo(BERLIN);
  }

  @Test
  void enthaeltDenBeginnAberNichtDasEnde() {
    NightRunPeriod tag =
        NightRunPeriod.of(NightRunPeriodType.DAY, BERLIN, am("2026-09-16T10:00:00Z"), 0);

    assertThat(tag.contains(tag.from())).isTrue();
    assertThat(tag.contains(tag.to().minusNanos(1))).isTrue();
    assertThat(tag.contains(tag.to())).isFalse();
    assertThat(tag.contains(tag.from().minusNanos(1))).isFalse();
  }

  @Test
  void nennt_genauDreiArten() {
    assertThat(NightRunPeriodType.values())
        .containsExactly(NightRunPeriodType.DAY, NightRunPeriodType.WEEK, NightRunPeriodType.MONTH);
  }
}
