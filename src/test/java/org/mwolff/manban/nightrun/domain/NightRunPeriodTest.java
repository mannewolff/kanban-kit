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

/**
 * Zeitraumgrenzen der Verbrauchs-Auswertung (Issue #934, Plan #933 E14, E18, E19) mit der
 * Tagesgrenze 12:00 zonenlokal (Issue #969): Ein Lauf vor 12:00 zählt zur vorangegangenen Nacht.
 *
 * <p>Zeiten im Kommentar sind Berliner Ortszeit; im September gilt CEST (UTC+2), ab dem 25.10. und
 * bis zum 29.03. CET (UTC+1).
 */
class NightRunPeriodTest {

  private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

  private static Clock am(String instant) {
    return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
  }

  private static NightRunPeriod nacht(String jetzt) {
    return NightRunPeriod.of(NightRunPeriodType.DAY, BERLIN, am(jetzt), 0);
  }

  // --- Tag -------------------------------------------------------------------------------------

  /** 16.09. 10:00 — die Nacht vom 15. auf den 16. läuft bis 12:00 noch. */
  @Test
  void vorZwoelfUhrIstDieNachtVomVortagNochNichtAbgeschlossen() {
    NightRunPeriod tag = nacht("2026-09-16T08:00:00Z");

    assertThat(tag.type()).isEqualTo(NightRunPeriodType.DAY);
    assertThat(tag.firstDay()).isEqualTo(LocalDate.of(2026, 9, 14));
    assertThat(tag.lastDay()).isEqualTo(LocalDate.of(2026, 9, 14));
    assertThat(tag.from()).isEqualTo(Instant.parse("2026-09-14T10:00:00Z"));
    assertThat(tag.to()).isEqualTo(Instant.parse("2026-09-15T10:00:00Z"));
  }

  /** 16.09. 13:00 — die Nacht vom 15. auf den 16. ist abgeschlossen. */
  @Test
  void nachZwoelfUhrIstDieNachtVomVortagAbgeschlossen() {
    NightRunPeriod tag = nacht("2026-09-16T11:00:00Z");

    assertThat(tag.firstDay()).isEqualTo(LocalDate.of(2026, 9, 15));
    assertThat(tag.from()).isEqualTo(Instant.parse("2026-09-15T10:00:00Z"));
    assertThat(tag.to()).isEqualTo(Instant.parse("2026-09-16T10:00:00Z"));
  }

  /** Genau 12:00 gehört schon zur beginnenden Nacht. */
  @Test
  void genauZwoelfUhrBeginntDieNeueNacht() {
    assertThat(nacht("2026-09-16T10:00:00Z").firstDay()).isEqualTo(LocalDate.of(2026, 9, 15));
    assertThat(nacht("2026-09-16T09:59:59Z").firstDay()).isEqualTo(LocalDate.of(2026, 9, 14));
  }

  @Test
  void rueckschrittEinsIstDieNachtDavor() {
    NightRunPeriod tag =
        NightRunPeriod.of(NightRunPeriodType.DAY, BERLIN, am("2026-09-16T11:00:00Z"), 1);

    assertThat(tag.firstDay()).isEqualTo(LocalDate.of(2026, 9, 14));
  }

  /** Läufe um 23:10 und um 03:22 des Folgetages liegen in derselben Nacht. */
  @Test
  void laeufeUm2310UndUm0322BildenEineNacht() {
    NightRunPeriod tag = nacht("2026-09-16T11:00:00Z");

    assertThat(tag.firstDay()).isEqualTo(LocalDate.of(2026, 9, 15));
    assertThat(tag.contains(Instant.parse("2026-09-15T21:10:00Z"))).isTrue();
    assertThat(tag.contains(Instant.parse("2026-09-16T01:22:00Z"))).isTrue();
  }

  /** 11:59 gehört zur vorangegangenen Nacht, 12:00 zur beginnenden. */
  @Test
  void elfUhrNeunundfuenfzigGehoertZurVorangegangenenNacht() {
    NightRunPeriod tag = nacht("2026-09-16T11:00:00Z");

    assertThat(tag.contains(Instant.parse("2026-09-15T09:59:00Z"))).isFalse();
    assertThat(tag.contains(Instant.parse("2026-09-15T10:00:00Z"))).isTrue();
    assertThat(tag.contains(Instant.parse("2026-09-16T09:59:00Z"))).isTrue();
    assertThat(tag.contains(Instant.parse("2026-09-16T10:00:00Z"))).isFalse();
  }

  /** 16.09. 12:30 in Berlin ist in UTC noch 10:30 — dort liefe die Nacht noch. */
  @Test
  void dieNachtFolgtDerZone_nichtUtc() {
    assertThat(nacht("2026-09-16T10:30:00Z").firstDay()).isEqualTo(LocalDate.of(2026, 9, 15));
    assertThat(
            NightRunPeriod.of(NightRunPeriodType.DAY, ZoneOffset.UTC, am("2026-09-16T10:30:00Z"), 0)
                .firstDay())
        .isEqualTo(LocalDate.of(2026, 9, 14));
  }

  /** Nacht vom 28. auf den 29.03.2026: 12:00 CET bis 12:00 CEST, 23 Stunden. */
  @Test
  void dieNachtUeberDieMaerzUmstellungHatDreiundzwanzigStunden() {
    NightRunPeriod tag = nacht("2026-03-29T12:00:00Z");

    assertThat(tag.firstDay()).isEqualTo(LocalDate.of(2026, 3, 28));
    assertThat(tag.from()).isEqualTo(Instant.parse("2026-03-28T11:00:00Z"));
    assertThat(tag.to()).isEqualTo(Instant.parse("2026-03-29T10:00:00Z"));
    assertThat(Duration.between(tag.from(), tag.to())).isEqualTo(Duration.ofHours(23));
  }

  /** Nacht vom 24. auf den 25.10.2026: 12:00 CEST bis 12:00 CET, 25 Stunden. */
  @Test
  void dieNachtUeberDieOktoberUmstellungHatFuenfundzwanzigStunden() {
    NightRunPeriod tag = nacht("2026-10-25T12:00:00Z");

    assertThat(tag.firstDay()).isEqualTo(LocalDate.of(2026, 10, 24));
    assertThat(tag.from()).isEqualTo(Instant.parse("2026-10-24T10:00:00Z"));
    assertThat(tag.to()).isEqualTo(Instant.parse("2026-10-25T11:00:00Z"));
    assertThat(Duration.between(tag.from(), tag.to())).isEqualTo(Duration.ofHours(25));
  }

  /** Die einzelne Nacht aus ihrem Datum (Issue #938): 12:00 am Datum bis 12:00 am Folgetag. */
  @Test
  void eineNachtAusIhremDatum() {
    NightRunPeriod nacht = NightRunPeriod.night(LocalDate.of(2026, 3, 28), BERLIN);

    assertThat(nacht.type()).isEqualTo(NightRunPeriodType.DAY);
    assertThat(nacht.zone()).isEqualTo(BERLIN);
    assertThat(nacht.firstDay()).isEqualTo(LocalDate.of(2026, 3, 28));
    assertThat(nacht.from()).isEqualTo(Instant.parse("2026-03-28T11:00:00Z"));
    assertThat(nacht.to()).isEqualTo(Instant.parse("2026-03-29T10:00:00Z"));
  }

  /**
   * Die laufende Nacht aus einem Zeitpunkt (Issue #1093): die Nacht, in der {@code jetzt} liegt —
   * anders als {@link NightRunPeriod#of} bewusst der laufende und nicht der zuletzt abgeschlossene
   * Zeitraum.
   */
  @Test
  void dieLaufendeNachtIstDieNachtInDerDerZeitpunktLiegt() {
    Instant elfUhrNeunundfuenfzig = Instant.parse("2026-09-16T09:59:00Z");
    Instant zwoelfUhr = Instant.parse("2026-09-16T10:00:00Z");

    assertThat(NightRunPeriod.laufendeNacht(elfUhrNeunundfuenfzig, BERLIN).firstDay())
        .as("11:59 zonenlokal gehört noch zur Nacht des Vortages")
        .isEqualTo(LocalDate.of(2026, 9, 15));

    NightRunPeriod laufende = NightRunPeriod.laufendeNacht(zwoelfUhr, BERLIN);
    assertThat(laufende.firstDay())
        .as("12:00 zonenlokal beginnt die Nacht des laufenden Tages")
        .isEqualTo(LocalDate.of(2026, 9, 16));
    assertThat(laufende.type()).isEqualTo(NightRunPeriodType.DAY);
    assertThat(laufende.zone()).isEqualTo(BERLIN);
    assertThat(laufende.lastDay()).isEqualTo(LocalDate.of(2026, 9, 16));
    assertThat(laufende.from())
        .as("Beginn: 12:00 des Beginn-Tages, einschließlich")
        .isEqualTo(zwoelfUhr);
    assertThat(laufende.to())
        .as("Ende: 12:00 des Folgetages, ausschließlich")
        .isEqualTo(Instant.parse("2026-09-17T10:00:00Z"));
    assertThat(laufende.contains(zwoelfUhr)).as("jetzt liegt in der gelieferten Spanne").isTrue();
    assertThat(laufende.contains(laufende.to())).isFalse();

    Instant zwoelfUhrDreissigBerlin = Instant.parse("2026-09-16T10:30:00Z");
    assertThat(NightRunPeriod.laufendeNacht(zwoelfUhrDreissigBerlin, BERLIN).firstDay())
        .as("12:30 in Berlin — die neue Nacht läuft schon")
        .isEqualTo(LocalDate.of(2026, 9, 16));
    assertThat(NightRunPeriod.laufendeNacht(zwoelfUhrDreissigBerlin, ZoneOffset.UTC).firstDay())
        .as("derselbe Zeitpunkt ist in UTC erst 10:30 — dort läuft noch die Nacht davor")
        .isEqualTo(LocalDate.of(2026, 9, 15));

    NightRunPeriod maerz =
        NightRunPeriod.laufendeNacht(Instant.parse("2026-03-28T20:00:00Z"), BERLIN);
    assertThat(maerz.firstDay()).isEqualTo(LocalDate.of(2026, 3, 28));
    assertThat(Duration.between(maerz.from(), maerz.to()))
        .as("die Nacht über die Umstellung im März hat 23 Stunden")
        .isEqualTo(Duration.ofHours(23));

    NightRunPeriod oktober =
        NightRunPeriod.laufendeNacht(Instant.parse("2026-10-24T20:00:00Z"), BERLIN);
    assertThat(oktober.firstDay()).isEqualTo(LocalDate.of(2026, 10, 24));
    assertThat(Duration.between(oktober.from(), oktober.to()))
        .as("die Nacht über die Umstellung im Oktober hat 25 Stunden")
        .isEqualTo(Duration.ofHours(25));
  }

  // --- Woche -----------------------------------------------------------------------------------

  /**
   * Mi 16.09. 13:00; die zuletzt abgeschlossene Woche lief von Mo 07.09. 12:00 bis Mo 14.09. 12:00.
   */
  @Test
  void dieWocheBeginntMontagZwoelfUhr() {
    NightRunPeriod woche =
        NightRunPeriod.of(NightRunPeriodType.WEEK, BERLIN, am("2026-09-16T11:00:00Z"), 0);

    assertThat(woche.firstDay()).isEqualTo(LocalDate.of(2026, 9, 7));
    assertThat(woche.firstDay().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    assertThat(woche.lastDay()).isEqualTo(LocalDate.of(2026, 9, 13));
    assertThat(woche.from()).isEqualTo(Instant.parse("2026-09-07T10:00:00Z"));
    assertThat(woche.to()).isEqualTo(Instant.parse("2026-09-14T10:00:00Z"));
  }

  /** Die Nacht vom Sonntag auf den Montag gehört zur Woche des Sonntags. */
  @Test
  void dieNachtVonSonntagAufMontagGehoertZurWocheDesSonntags() {
    NightRunPeriod woche =
        NightRunPeriod.of(NightRunPeriodType.WEEK, BERLIN, am("2026-09-16T11:00:00Z"), 0);

    assertThat(woche.contains(Instant.parse("2026-09-13T22:30:00Z"))).isTrue(); // Mo 00:30
    assertThat(woche.contains(Instant.parse("2026-09-14T09:59:00Z"))).isTrue(); // Mo 11:59
    assertThat(woche.contains(Instant.parse("2026-09-14T10:00:00Z"))).isFalse(); // Mo 12:00
  }

  /** Mo 14.09. 10:00 läuft noch die Nacht vom Sonntag — und damit die Woche ab dem 07.09. */
  @Test
  void amMontagVormittagLaeuftNochDieVorwoche() {
    NightRunPeriod woche =
        NightRunPeriod.of(NightRunPeriodType.WEEK, BERLIN, am("2026-09-14T08:00:00Z"), 0);

    assertThat(woche.firstDay()).isEqualTo(LocalDate.of(2026, 8, 31));
  }

  @Test
  void amMontagNachmittagIstDieVorwocheAbgeschlossen() {
    NightRunPeriod woche =
        NightRunPeriod.of(NightRunPeriodType.WEEK, BERLIN, am("2026-09-14T11:00:00Z"), 0);

    assertThat(woche.firstDay()).isEqualTo(LocalDate.of(2026, 9, 7));
  }

  /** Mo 19.10. 12:00 CEST bis Mo 26.10. 12:00 CET — eine Stunde mehr. */
  @Test
  void eineWocheUeberDieOktoberUmstellungHatEineStundeMehr() {
    NightRunPeriod woche =
        NightRunPeriod.of(NightRunPeriodType.WEEK, BERLIN, am("2026-10-28T11:00:00Z"), 0);

    assertThat(woche.firstDay()).isEqualTo(LocalDate.of(2026, 10, 19));
    assertThat(woche.from()).isEqualTo(Instant.parse("2026-10-19T10:00:00Z"));
    assertThat(woche.to()).isEqualTo(Instant.parse("2026-10-26T11:00:00Z"));
    assertThat(Duration.between(woche.from(), woche.to()))
        .isEqualTo(Duration.ofDays(7).plusHours(1));
  }

  @Test
  void rueckschrittZweiIstDieWocheVorDerVorwoche() {
    NightRunPeriod woche =
        NightRunPeriod.of(NightRunPeriodType.WEEK, BERLIN, am("2026-09-16T11:00:00Z"), 2);

    assertThat(woche.firstDay()).isEqualTo(LocalDate.of(2026, 8, 24));
  }

  // --- Monat -----------------------------------------------------------------------------------

  @Test
  void derMonatBeginntAmErstenUmZwoelfUhr() {
    NightRunPeriod monat =
        NightRunPeriod.of(NightRunPeriodType.MONTH, BERLIN, am("2026-09-16T11:00:00Z"), 0);

    assertThat(monat.firstDay()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(monat.lastDay()).isEqualTo(LocalDate.of(2026, 8, 31));
    assertThat(monat.from()).isEqualTo(Instant.parse("2026-08-01T10:00:00Z"));
    assertThat(monat.to()).isEqualTo(Instant.parse("2026-09-01T10:00:00Z"));
  }

  /** Die Nacht vom 31. auf den 1. gehört zum alten Monat. */
  @Test
  void dieNachtVom31AufDen1GehoertZumAltenMonat() {
    NightRunPeriod august =
        NightRunPeriod.of(NightRunPeriodType.MONTH, BERLIN, am("2026-09-16T11:00:00Z"), 0);

    assertThat(august.contains(Instant.parse("2026-08-31T22:30:00Z"))).isTrue(); // 01.09. 00:30
    assertThat(august.contains(Instant.parse("2026-09-01T09:59:00Z"))).isTrue(); // 01.09. 11:59
    assertThat(august.contains(Instant.parse("2026-09-01T10:00:00Z"))).isFalse(); // 01.09. 12:00
  }

  /** Am 1. vor 12:00 läuft noch die letzte Nacht des Vormonats — abgeschlossen ist der davor. */
  @Test
  void amErstenVormittagLaeuftNochDerVormonat() {
    NightRunPeriod monat =
        NightRunPeriod.of(NightRunPeriodType.MONTH, BERLIN, am("2026-09-01T08:00:00Z"), 0);

    assertThat(monat.firstDay()).isEqualTo(LocalDate.of(2026, 7, 1));
  }

  /** Der März beginnt in Winterzeit (12:00 CET) und endet in Sommerzeit (12:00 CEST). */
  @Test
  void derMaerzBeginntInWinterzeitUndEndetInSommerzeit() {
    NightRunPeriod maerz =
        NightRunPeriod.of(NightRunPeriodType.MONTH, BERLIN, am("2026-04-10T10:00:00Z"), 0);

    assertThat(maerz.firstDay()).isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(maerz.from()).isEqualTo(Instant.parse("2026-03-01T11:00:00Z"));
    assertThat(maerz.to()).isEqualTo(Instant.parse("2026-04-01T10:00:00Z"));
  }

  @Test
  void rueckschrittUeberDenJahreswechsel() {
    NightRunPeriod monat =
        NightRunPeriod.of(NightRunPeriodType.MONTH, BERLIN, am("2026-02-10T10:00:00Z"), 1);

    assertThat(monat.firstDay()).isEqualTo(LocalDate.of(2025, 12, 1));
    assertThat(monat.lastDay()).isEqualTo(LocalDate.of(2025, 12, 31));
  }

  // --- Vorzeitraum und Eingaben ----------------------------------------------------------------

  @Test
  void derVorzeitraumIstDerUnmittelbarVorangegangeneGleichartige() {
    Clock jetzt = am("2026-09-16T11:00:00Z");
    for (NightRunPeriodType art : NightRunPeriodType.values()) {
      NightRunPeriod zeitraum = NightRunPeriod.of(art, BERLIN, jetzt, 0);

      NightRunPeriod vorher = zeitraum.previous();

      assertThat(vorher).isEqualTo(NightRunPeriod.of(art, BERLIN, jetzt, 1));
      assertThat(vorher.to()).isEqualTo(zeitraum.from());
    }
  }

  @Test
  void einNegativerRueckschrittWirdAbgewiesen() {
    Clock jetzt = am("2026-09-16T11:00:00Z");

    assertThatThrownBy(() -> NightRunPeriod.of(NightRunPeriodType.DAY, BERLIN, jetzt, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("-1");
  }

  @Test
  void rueckschrittNullIstErlaubt() {
    assertThat(nacht("2026-09-16T11:00:00Z").zone()).isEqualTo(BERLIN);
  }

  @Test
  void nennt_genauDreiArten() {
    assertThat(NightRunPeriodType.values())
        .containsExactly(NightRunPeriodType.DAY, NightRunPeriodType.WEEK, NightRunPeriodType.MONTH);
  }
}
