package org.mwolff.manban.entscheidungskette.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Die Kopfzeilen, die den Weg einer Karte belegen (Issue #2115). */
class DokumentzeilenTest {

  @Test
  void ohneText_keineZeile() {
    assertThat(Dokumentzeilen.traegtFachlicheQuelle(null)).isFalse();
    assertThat(Dokumentzeilen.traegtPlanModell(null)).isFalse();
    assertThat(Dokumentzeilen.modell(null)).isEmpty();
    assertThat(Dokumentzeilen.planNummern(null)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"Fachliche Quelle: Issue #1", "  Requirement: offen"})
  void fachlicheQuelle_deutschUndEnglisch(String zeile) {
    assertThat(Dokumentzeilen.traegtFachlicheQuelle("Kopf\n" + zeile + "\n")).isTrue();
  }

  @Test
  void fachlicheQuelle_mitten_inDerZeile_zaehltNicht() {
    assertThat(Dokumentzeilen.traegtFachlicheQuelle("Siehe Fachliche Quelle: #1")).isFalse();
  }

  @Test
  void modell_planModellVorAutorModell() {
    assertThat(Dokumentzeilen.modell("Autor-Modell: a\nPlan-Modell:  b  \n")).contains("b");
  }

  @Test
  void nummern_inTextfolge() {
    assertThat(Dokumentzeilen.planNummern("Plan: #3\nPlan: Issue #1\nPlan: #12345678901\n"))
        .containsExactly(3, 1);
    assertThat(Dokumentzeilen.quellNummern("Requirement: #4\nFachliche Quelle: Issue #5"))
        .containsExactly(4, 5);
  }

  @ParameterizedTest
  @CsvSource({"'Issue #12',12", "'#7 und mehr',7", "'Issue  #8',8"})
  void ergebnisNummer_amAnfang(String ergebnis, int nummer) {
    assertThat(Dokumentzeilen.ergebnisNummer(ergebnis)).contains(nummer);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Commit abc", "Plan #12", "#1234567890"})
  void ergebnisNummer_ohneNummerAmAnfang_leer(String ergebnis) {
    assertThat(Dokumentzeilen.ergebnisNummer(ergebnis)).isEmpty();
  }
}
