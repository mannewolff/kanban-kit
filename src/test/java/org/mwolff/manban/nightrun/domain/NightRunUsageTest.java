package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Summe und Zwischenspeicher-Anteil des Verbrauchs (Issue #934, Plan #933 E5). */
class NightRunUsageTest {

  private static final NightRunUsage NICHTS = new NightRunUsage(null, null, null, null, null, null);

  @Test
  void fehlendPlusFehlendBleibtInJedemFeldFehlend_undWirdNie0() {
    NightRunUsage summe = NICHTS.plus(NICHTS);

    assertThat(summe.costUsd()).isNull();
    assertThat(summe.inputTokens()).isNull();
    assertThat(summe.outputTokens()).isNull();
    assertThat(summe.cachedInputTokens()).isNull();
  }

  @Test
  void fehlendPlusVorhandenErgibtDenVorhandenen_inBeidenRichtungen() {
    NightRunUsage vorhanden = new NightRunUsage(new BigDecimal("1.25"), 10L, 20L, 5L, null, null);

    assertThat(NICHTS.plus(vorhanden)).isEqualTo(vorhanden);
    assertThat(vorhanden.plus(NICHTS)).isEqualTo(vorhanden);
  }

  @Test
  void summiertFeldweise() {
    NightRunUsage a = new NightRunUsage(new BigDecimal("1.25"), 10L, 20L, 5L, null, null);
    NightRunUsage b = new NightRunUsage(new BigDecimal("0.75"), 1L, 2L, 3L, null, null);

    NightRunUsage summe = a.plus(b);

    assertThat(summe.costUsd()).isEqualByComparingTo("2.00");
    assertThat(summe.inputTokens()).isEqualTo(11L);
    assertThat(summe.outputTokens()).isEqualTo(22L);
    assertThat(summe.cachedInputTokens()).isEqualTo(8L);
  }

  /** Jedes Feld fuer sich: Ein fehlendes Feld in einem Summanden ueberdeckt nicht die anderen. */
  @Test
  void summiertJedesFeldUnabhaengig() {
    NightRunUsage nurKosten =
        new NightRunUsage(new BigDecimal("1.00"), null, null, null, null, null);
    NightRunUsage nurMengen = new NightRunUsage(null, 100L, 50L, 80L, null, null);

    NightRunUsage summe = nurKosten.plus(nurMengen);

    assertThat(summe)
        .isEqualTo(new NightRunUsage(new BigDecimal("1.00"), 100L, 50L, 80L, null, null));
  }

  // --- Differenz: der nicht zuordenbare Rest (Issue #938, Plan #933 E6) -------------------------

  @Test
  void minusRechnetFeldweise() {
    NightRunUsage lauf = new NightRunUsage(new BigDecimal("10.00"), 1_000L, 100L, 900L, null, null);
    NightRunUsage pakete = new NightRunUsage(new BigDecimal("4.00"), 400L, 40L, 360L, null, null);

    NightRunUsage rest = lauf.minus(pakete);

    assertThat(rest.costUsd()).isEqualByComparingTo("6.00");
    assertThat(rest.inputTokens()).isEqualTo(600L);
    assertThat(rest.outputTokens()).isEqualTo(60L);
    assertThat(rest.cachedInputTokens()).isEqualTo(540L);
  }

  /** Fehlt eine Seite, ist die Differenz nicht bestimmt — sie wird nicht zum vorhandenen Wert. */
  @Test
  void minusMitEinerFehlendenSeiteBleibtFehlend_inBeidenRichtungen() {
    NightRunUsage nurKosten =
        new NightRunUsage(new BigDecimal("1.00"), null, null, null, null, null);
    NightRunUsage nurMengen = new NightRunUsage(null, 100L, 50L, 80L, null, null);

    assertThat(nurKosten.minus(nurMengen)).isEqualTo(NICHTS);
    assertThat(nurMengen.minus(nurKosten)).isEqualTo(NICHTS);
  }

  // --- Modellzeit und Zuege (Issue #1112, Plan #1110 E1) ---------------------------------------

  /**
   * Die beiden neuen Felder folgen derselben Regel wie die vier alten: {@code null} heisst „nicht
   * gemessen" und wird in keiner Summe zu 0.
   */
  @Test
  void modellzeitUndZuegeFehlendPlusFehlendBleibenFehlend() {
    NightRunUsage summe = NICHTS.plus(NICHTS);

    assertThat(summe.modelDurationMs()).isNull();
    assertThat(summe.turns()).isNull();
  }

  @Test
  void modellzeitUndZuegeFehlendPlusVorhandenErgebenDenVorhandenen_inBeidenRichtungen() {
    NightRunUsage vorhanden = new NightRunUsage(null, null, null, null, 7_000L, 12);

    assertThat(NICHTS.plus(vorhanden).modelDurationMs()).isEqualTo(7_000L);
    assertThat(NICHTS.plus(vorhanden).turns()).isEqualTo(12);
    assertThat(vorhanden.plus(NICHTS).modelDurationMs()).isEqualTo(7_000L);
    assertThat(vorhanden.plus(NICHTS).turns()).isEqualTo(12);
  }

  @Test
  void modellzeitUndZuegeSummierenFeldweise() {
    NightRunUsage a = new NightRunUsage(null, null, null, null, 7_000L, 12);
    NightRunUsage b = new NightRunUsage(null, null, null, null, 500L, 3);

    NightRunUsage summe = a.plus(b);

    assertThat(summe.modelDurationMs()).isEqualTo(7_500L);
    assertThat(summe.turns()).isEqualTo(15);
  }

  @Test
  void modellzeitUndZuegeRechnenInDerDifferenzFeldweise() {
    NightRunUsage lauf = new NightRunUsage(null, null, null, null, 7_000L, 12);
    NightRunUsage pakete = new NightRunUsage(null, null, null, null, 2_000L, 5);

    NightRunUsage rest = lauf.minus(pakete);

    assertThat(rest.modelDurationMs()).isEqualTo(5_000L);
    assertThat(rest.turns()).isEqualTo(7);
  }

  /** Fehlt eine Seite, bleibt die Differenz unbestimmt — sie wird nicht zum vorhandenen Wert. */
  @Test
  void modellzeitUndZuegeBleibenMitEinerFehlendenSeiteFehlend_inBeidenRichtungen() {
    NightRunUsage vorhanden = new NightRunUsage(null, null, null, null, 7_000L, 12);

    assertThat(vorhanden.minus(NICHTS).modelDurationMs()).isNull();
    assertThat(vorhanden.minus(NICHTS).turns()).isNull();
    assertThat(NICHTS.minus(vorhanden).modelDurationMs()).isNull();
    assertThat(NICHTS.minus(vorhanden).turns()).isNull();
  }

  /**
   * Jedes Feld fuer sich, auch ueber die Feldtypen hinweg: Eine gemessene Modellzeit ohne Zuege und
   * umgekehrt bleiben nebeneinander stehen.
   */
  @Test
  void modellzeitUndZuegeSummierenUnabhaengigVoneinander() {
    NightRunUsage nurZeit = new NightRunUsage(null, null, null, null, 7_000L, null);
    NightRunUsage nurZuege = new NightRunUsage(null, null, null, null, null, 12);

    assertThat(nurZeit.plus(nurZuege))
        .isEqualTo(new NightRunUsage(null, null, null, null, 7_000L, 12));
    assertThat(nurZeit.minus(nurZuege)).isEqualTo(NICHTS);
  }

  @Test
  void zwischenspeicherAnteilInProzent() {
    NightRunUsage verbrauch = new NightRunUsage(null, 200L, null, 50L, null, null);

    assertThat(verbrauch.cachedInputSharePercent()).isEqualByComparingTo("25.00");
  }

  @Test
  void zwischenspeicherAnteilRundetAufZweiNachkommastellen() {
    NightRunUsage verbrauch = new NightRunUsage(null, 3L, null, 2L, null, null);

    assertThat(verbrauch.cachedInputSharePercent()).isEqualTo(new BigDecimal("66.67"));
  }

  @Test
  void zwischenspeicherAnteilOhneEingabemengeIstNichtBestimmt() {
    assertThat(new NightRunUsage(null, null, null, 50L, null, null).cachedInputSharePercent())
        .isNull();
  }

  @Test
  void zwischenspeicherAnteilOhneZwischenspeicherMengeIstNichtBestimmt() {
    assertThat(new NightRunUsage(null, 200L, null, null, null, null).cachedInputSharePercent())
        .isNull();
  }

  /** Eine gemessene Eingabemenge von 0 laesst keinen Anteil zu — und wird nicht zu 0 %. */
  @Test
  void zwischenspeicherAnteilBeiEingabemengeNullIstNichtBestimmt() {
    assertThat(new NightRunUsage(null, 0L, null, 0L, null, null).cachedInputSharePercent())
        .isNull();
  }

  @Test
  void keinZwischenspeicherBeiGemessenerEingabeIstNullProzent() {
    assertThat(new NightRunUsage(null, 200L, null, 0L, null, null).cachedInputSharePercent())
        .isEqualByComparingTo("0");
  }
}
