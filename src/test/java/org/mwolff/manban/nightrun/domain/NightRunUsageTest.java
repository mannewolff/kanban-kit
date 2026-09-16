package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Summe und Zwischenspeicher-Anteil des Verbrauchs (Issue #934, Plan #933 E5). */
class NightRunUsageTest {

  private static final NightRunUsage NICHTS = new NightRunUsage(null, null, null, null);

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
    NightRunUsage vorhanden = new NightRunUsage(new BigDecimal("1.25"), 10L, 20L, 5L);

    assertThat(NICHTS.plus(vorhanden)).isEqualTo(vorhanden);
    assertThat(vorhanden.plus(NICHTS)).isEqualTo(vorhanden);
  }

  @Test
  void summiertFeldweise() {
    NightRunUsage a = new NightRunUsage(new BigDecimal("1.25"), 10L, 20L, 5L);
    NightRunUsage b = new NightRunUsage(new BigDecimal("0.75"), 1L, 2L, 3L);

    NightRunUsage summe = a.plus(b);

    assertThat(summe.costUsd()).isEqualByComparingTo("2.00");
    assertThat(summe.inputTokens()).isEqualTo(11L);
    assertThat(summe.outputTokens()).isEqualTo(22L);
    assertThat(summe.cachedInputTokens()).isEqualTo(8L);
  }

  /** Jedes Feld fuer sich: Ein fehlendes Feld in einem Summanden ueberdeckt nicht die anderen. */
  @Test
  void summiertJedesFeldUnabhaengig() {
    NightRunUsage nurKosten = new NightRunUsage(new BigDecimal("1.00"), null, null, null);
    NightRunUsage nurMengen = new NightRunUsage(null, 100L, 50L, 80L);

    NightRunUsage summe = nurKosten.plus(nurMengen);

    assertThat(summe).isEqualTo(new NightRunUsage(new BigDecimal("1.00"), 100L, 50L, 80L));
  }

  // --- Differenz: der nicht zuordenbare Rest (Issue #938, Plan #933 E6) -------------------------

  @Test
  void minusRechnetFeldweise() {
    NightRunUsage lauf = new NightRunUsage(new BigDecimal("10.00"), 1_000L, 100L, 900L);
    NightRunUsage pakete = new NightRunUsage(new BigDecimal("4.00"), 400L, 40L, 360L);

    NightRunUsage rest = lauf.minus(pakete);

    assertThat(rest.costUsd()).isEqualByComparingTo("6.00");
    assertThat(rest.inputTokens()).isEqualTo(600L);
    assertThat(rest.outputTokens()).isEqualTo(60L);
    assertThat(rest.cachedInputTokens()).isEqualTo(540L);
  }

  /** Fehlt eine Seite, ist die Differenz nicht bestimmt — sie wird nicht zum vorhandenen Wert. */
  @Test
  void minusMitEinerFehlendenSeiteBleibtFehlend_inBeidenRichtungen() {
    NightRunUsage nurKosten = new NightRunUsage(new BigDecimal("1.00"), null, null, null);
    NightRunUsage nurMengen = new NightRunUsage(null, 100L, 50L, 80L);

    assertThat(nurKosten.minus(nurMengen)).isEqualTo(NICHTS);
    assertThat(nurMengen.minus(nurKosten)).isEqualTo(NICHTS);
  }

  @Test
  void zwischenspeicherAnteilInProzent() {
    NightRunUsage verbrauch = new NightRunUsage(null, 200L, null, 50L);

    assertThat(verbrauch.cachedInputSharePercent()).isEqualByComparingTo("25.00");
  }

  @Test
  void zwischenspeicherAnteilRundetAufZweiNachkommastellen() {
    NightRunUsage verbrauch = new NightRunUsage(null, 3L, null, 2L);

    assertThat(verbrauch.cachedInputSharePercent()).isEqualTo(new BigDecimal("66.67"));
  }

  @Test
  void zwischenspeicherAnteilOhneEingabemengeIstNichtBestimmt() {
    assertThat(new NightRunUsage(null, null, null, 50L).cachedInputSharePercent()).isNull();
  }

  @Test
  void zwischenspeicherAnteilOhneZwischenspeicherMengeIstNichtBestimmt() {
    assertThat(new NightRunUsage(null, 200L, null, null).cachedInputSharePercent()).isNull();
  }

  /** Eine gemessene Eingabemenge von 0 laesst keinen Anteil zu — und wird nicht zu 0 %. */
  @Test
  void zwischenspeicherAnteilBeiEingabemengeNullIstNichtBestimmt() {
    assertThat(new NightRunUsage(null, 0L, null, 0L).cachedInputSharePercent()).isNull();
  }

  @Test
  void keinZwischenspeicherBeiGemessenerEingabeIstNullProzent() {
    assertThat(new NightRunUsage(null, 200L, null, 0L).cachedInputSharePercent())
        .isEqualByComparingTo("0");
  }
}
