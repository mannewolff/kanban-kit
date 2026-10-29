package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Stand, Warnstufe, Hochrechnung und Überschreitungstag des Monatsbudgets (Issue #1966, Plan #1962,
 * fachliche Quelle #1957 AK 5 bis 11) — reine Rechnung ohne Uhr und ohne Datenbank.
 */
class MonatsbudgetStandTest {

  private static final LocalDate OKTOBER = LocalDate.of(2026, 10, 1);

  private static MonatsbudgetStand stand(
      @Nullable String budget, @Nullable String verbrauch, int angebrocheneTage, int tage) {
    return new MonatsbudgetStand(
        budget == null ? null : new BigDecimal(budget),
        verbrauch == null ? null : new BigDecimal(verbrauch),
        OKTOBER,
        angebrocheneTage,
        tage);
  }

  // --- Szenarien aus der fachlichen Quelle #1957 ----------------------------------------------

  @Test
  @DisplayName("Szenario: Hochrechnung über dem Budget")
  void hochrechnungUeberDemBudget() {
    MonatsbudgetStand stand = stand("100", "40", 10, 31);

    assertThat(stand.percent()).isEqualTo(40);
    assertThat(stand.forecastUsd()).isEqualByComparingTo("124");
    assertThat(stand.tooEarly()).isFalse();
    assertThat(stand.exceedDate()).isEqualTo(LocalDate.of(2026, 10, 25));
    assertThat(stand.warning()).isEqualTo(Warnstufe.NONE);
  }

  @Test
  @DisplayName("Szenario: Warnung bei 80 %")
  void warnungBeiAchtzigProzent() {
    MonatsbudgetStand stand = stand("100", "85", 10, 31);

    assertThat(stand.percent()).isEqualTo(85);
    assertThat(stand.warning()).isEqualTo(Warnstufe.EIGHTY);
  }

  @Test
  @DisplayName("Szenario: Budget überschritten")
  void budgetUeberschritten() {
    MonatsbudgetStand stand = stand("100", "110", 20, 31);

    assertThat(stand.percent()).isEqualTo(110);
    assertThat(stand.warning()).isEqualTo(Warnstufe.REACHED);
    assertThat(stand.forecastUsd()).isEqualByComparingTo("170.50");
    assertThat(stand.exceedDate()).as("Stand schon über dem Budget — kein Hinweis").isNull();
  }

  @Test
  @DisplayName("Szenario: Zu früh für eine Hochrechnung")
  void zuFruehFuerEineHochrechnung() {
    MonatsbudgetStand stand = stand("100", "40", 2, 31);

    assertThat(stand.forecastUsd()).isNull();
    assertThat(stand.tooEarly()).isTrue();
    assertThat(stand.exceedDate()).as("ohne Hochrechnung kein Überschreitungstag").isNull();
    assertThat(stand.percent()).as("der Stand steht trotzdem").isEqualTo(40);
  }

  @Test
  @DisplayName("Szenario: Ohne Budget")
  void ohneBudget() {
    MonatsbudgetStand stand = stand(null, "40", 10, 31);

    assertThat(stand.forecastUsd()).isEqualByComparingTo("124");
    assertThat(stand.percent()).isNull();
    assertThat(stand.warning()).isEqualTo(Warnstufe.NONE);
    assertThat(stand.exceedDate()).isNull();
  }

  // --- Randfälle -------------------------------------------------------------------------------

  /** 79,6 % — abgerundet 79, und verglichen wird am genauen Betrag: keine Warnung. */
  @Test
  void knappUnterAchtzigProzentOhneWarnung() {
    MonatsbudgetStand stand = stand("100", "79.60", 10, 31);

    assertThat(stand.percent()).isEqualTo(79);
    assertThat(stand.warning()).isEqualTo(Warnstufe.NONE);
  }

  @Test
  void genauAchtzigProzentMitWarnung() {
    assertThat(stand("100", "80", 10, 31).warning()).isEqualTo(Warnstufe.EIGHTY);
    assertThat(stand("100", "79.99", 10, 31).warning()).isEqualTo(Warnstufe.NONE);
  }

  @Test
  void genauHundertProzentErreichtDasBudget() {
    assertThat(stand("100", "100", 10, 31).warning()).isEqualTo(Warnstufe.REACHED);
    assertThat(stand("100", "99.99", 10, 31).warning()).isEqualTo(Warnstufe.EIGHTY);
    assertThat(stand("100", "99.99", 10, 31).percent()).isEqualTo(99);
    assertThat(stand("100", "100", 10, 31).exceedDate())
        .as("erreicht ist nicht mehr voraussichtlich überschritten")
        .isNull();
  }

  /** 100 × 10 / 43 = 23,26 — an dessen Ende ist das Budget erst am 24. erreicht. */
  @Test
  void ueberschreitungstagWirdAufgerundet() {
    MonatsbudgetStand stand = stand("100", "43", 10, 31);

    assertThat(stand.exceedDate()).isEqualTo(LocalDate.of(2026, 10, 24));
  }

  /** Die Hochrechnung trifft das Budget genau — „über dem Budget“ ist sie damit nicht. */
  @Test
  void hochrechnungGleichBudgetOhneUeberschreitungstag() {
    MonatsbudgetStand stand = stand("31", "10", 10, 31);

    assertThat(stand.forecastUsd()).isEqualByComparingTo("31");
    assertThat(stand.exceedDate()).isNull();
  }

  @Test
  void hochrechnungWirdAufCentGerundet() {
    assertThat(stand(null, "10", 3, 31).forecastUsd()).isEqualByComparingTo("103.33");
    assertThat(stand(null, "20", 3, 31).forecastUsd()).isEqualByComparingTo("206.67");
  }

  @Test
  void verbrauchNullIstEinNormalerStand() {
    MonatsbudgetStand stand = stand("100", "0", 10, 31);

    assertThat(stand.percent()).isZero();
    assertThat(stand.warning()).isEqualTo(Warnstufe.NONE);
    assertThat(stand.forecastUsd()).isEqualByComparingTo("0");
    assertThat(stand.tooEarly()).isFalse();
    assertThat(stand.exceedDate()).isNull();
  }

  /** E2: Läufe ohne gemeldete Kosten — eine Zahl, die es nicht gibt, ergibt nichts. */
  @Test
  void verbrauchUnbekanntErgibtWederAnteilNochWarnungNochHochrechnung() {
    MonatsbudgetStand stand = stand("100", null, 10, 31);

    assertThat(stand.percent()).isNull();
    assertThat(stand.warning()).isEqualTo(Warnstufe.NONE);
    assertThat(stand.forecastUsd()).isNull();
    assertThat(stand.exceedDate()).isNull();
    assertThat(stand.tooEarly()).as("hängt allein an den Tagen").isFalse();
  }

  @Test
  void hochrechnungRechnetMitDerLaengeDesMonats() {
    assertThat(stand(null, "40", 10, 28).forecastUsd()).isEqualByComparingTo("112");
    assertThat(stand(null, "40", 10, 30).forecastUsd()).isEqualByComparingTo("120");
    assertThat(stand(null, "40", 10, 31).forecastUsd()).isEqualByComparingTo("124");
  }

  /** Im Februar mit 28 Tagen: 100 × 10 / 36 = 27,8 → 28. Februar, der letzte Tag. */
  @Test
  void ueberschreitungstagAmLetztenTagDesKurzenMonats() {
    MonatsbudgetStand stand =
        new MonatsbudgetStand(
            new BigDecimal("100"), new BigDecimal("36"), LocalDate.of(2026, 2, 1), 10, 28);

    assertThat(stand.exceedDate()).isEqualTo(LocalDate.of(2026, 2, 28));
  }

  @Test
  void abDemDrittenTagStehtDieHochrechnung() {
    assertThat(stand(null, "3", 1, 31).tooEarly()).isTrue();
    assertThat(stand(null, "3", 3, 31).tooEarly()).isFalse();
    assertThat(stand(null, "3", 3, 31).forecastUsd()).isEqualByComparingTo("31");
  }

  @Test
  void nenntGenauDreiWarnstufen() {
    assertThat(Warnstufe.values())
        .containsExactly(Warnstufe.NONE, Warnstufe.EIGHTY, Warnstufe.REACHED);
  }
}
