package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Die Zuordnung eines gemeldeten Stufennamens zur Stufe (Issue #1150).
 *
 * <p>Das Kit schickt die Namen klein geschrieben ({@code board.mjs}, {@code NACHTLAUF_STUFEN}), der
 * Vertrag nennt sie groß. Die Toleranz gilt allein der Schreibweise: Ein Name außerhalb des
 * Wertebereichs bleibt ein Vertragsbruch und wird abgewiesen.
 */
class NightRunStageTest {

  @ParameterizedTest
  @ValueSource(strings = {"plan", "Plan", "PLAN", "pLaN"})
  void jedeSchreibweiseEinesBekanntenNamensFindetSeineStufe(String name) {
    assertThat(NightRunStage.vonName(name)).isEqualTo(NightRunStage.PLAN);
  }

  @Test
  void alleVierStufenSindKleinGeschriebenErreichbar() {
    assertThat(NightRunStage.vonName("review")).isEqualTo(NightRunStage.REVIEW);
    assertThat(NightRunStage.vonName("pakete")).isEqualTo(NightRunStage.PAKETE);
    assertThat(NightRunStage.vonName("abdeckung")).isEqualTo(NightRunStage.ABDECKUNG);
  }

  @ParameterizedTest
  @ValueSource(strings = {"umsetzung", "", " ", "plan "})
  void einNameAusserhalbDesWertebereichsWirdAbgewiesen(String name) {
    assertThatThrownBy(() -> NightRunStage.vonName(name))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
