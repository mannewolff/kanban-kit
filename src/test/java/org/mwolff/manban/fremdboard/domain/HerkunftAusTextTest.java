package org.mwolff.manban.fremdboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Herkunft einer Karte des fremden Boards aus ihrem Text (Issue #2158, Plan #2152 A12). */
class HerkunftAusTextTest {

  @Test
  void fachlicheQuelle_ausGanzerZeile() {
    String text = "## Kontext\nWarum.\nFachliche Quelle: Issue #2150\nPlan: Issue #2152\n";

    assertThat(HerkunftAusText.fachlicheQuelle(text)).contains(2150L);
  }

  @Test
  void fachlicheQuelle_auchUnterDemEnglischenNamen() {
    assertThat(HerkunftAusText.fachlicheQuelle("Requirement: Issue #7")).contains(7L);
  }

  @Test
  void fachlicheQuelle_brauchtIssue() {
    assertThat(HerkunftAusText.fachlicheQuelle("Fachliche Quelle: #2150")).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Plan: Issue #2152",
        "Plan: #2152",
        "plan: issue #2152",
        "  Plan:   Issue   #2152  ",
        "\tPlan:#2152",
        "Plan: Issue #002152"
      })
  void plan_mitUndOhneIssue(String zeile) {
    assertThat(HerkunftAusText.plan("Kontext\n" + zeile + "\nRest")).contains(2152L);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Siehe Plan: Issue #5",
        "Plan: Issue #5 und #6",
        "Plan: Issue 5",
        "Plan-Entscheidungen: A3, A12",
        "Plan-Modell: claude",
        "Plan: Issue #",
        "Plan: Issue #99999999999999999999",
        ""
      })
  void plan_nurAusGanzerZeileMitNummer(String zeile) {
    assertThat(HerkunftAusText.plan(zeile)).isEmpty();
  }

  @Test
  void ersteZeileGilt() {
    assertThat(HerkunftAusText.plan("Plan: #3\nPlan: #4")).contains(3L);
  }

  @Test
  void zeilenendenAllerArten() {
    assertThat(HerkunftAusText.plan("a\r\nPlan: #11\r\nb")).contains(11L);
    assertThat(HerkunftAusText.fachlicheQuelle("a\rFachliche Quelle: Issue #12\rb")).contains(12L);
  }

  @Test
  void ohneText_keineHerkunft() {
    assertThat(HerkunftAusText.plan(null)).isEmpty();
    assertThat(HerkunftAusText.fachlicheQuelle(null)).isEmpty();
  }
}
