package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Die Zuordnung eines gemeldeten Stufennamens zur Stufe eines Vorgangs (Issue #1944, Plan #1905 E2)
 * — tolerant in der Schreibweise wie {@link NightRunStage#vonName}, streng im Wertebereich.
 */
class NightRunAttemptStageTest {

  @ParameterizedTest
  @ValueSource(strings = {"umsetzung", "Umsetzung", "UMSETZUNG", "uMsEtZuNg"})
  void jedeSchreibweiseEinesBekanntenNamensFindetSeineStufe(String name) {
    assertThat(NightRunAttemptStage.vonName(name)).isEqualTo(NightRunAttemptStage.UMSETZUNG);
  }

  @Test
  void alleFuenfStufenSindKleinGeschriebenErreichbar() {
    assertThat(NightRunAttemptStage.vonName("plan")).isEqualTo(NightRunAttemptStage.PLAN);
    assertThat(NightRunAttemptStage.vonName("review")).isEqualTo(NightRunAttemptStage.REVIEW);
    assertThat(NightRunAttemptStage.vonName("pakete")).isEqualTo(NightRunAttemptStage.PAKETE);
    assertThat(NightRunAttemptStage.vonName("abdeckung")).isEqualTo(NightRunAttemptStage.ABDECKUNG);
  }

  @Test
  void englischeNamenFindenDieDeutscheStufe() {
    assertThat(NightRunAttemptStage.vonName("PACKAGES")).isEqualTo(NightRunAttemptStage.PAKETE);
    assertThat(NightRunAttemptStage.vonName("coverage")).isEqualTo(NightRunAttemptStage.ABDECKUNG);
    assertThat(NightRunAttemptStage.vonName("Implementation"))
        .isEqualTo(NightRunAttemptStage.UMSETZUNG);
  }

  @ParameterizedTest
  @ValueSource(strings = {"pruefung", "", " ", "plan "})
  void einNameAusserhalbDesWertebereichsWirdAbgewiesen(String name) {
    assertThatThrownBy(() -> NightRunAttemptStage.vonName(name))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
