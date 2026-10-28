package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Die gemeinsamen Signalregeln der Platten „Nacharbeit“ und „Modelle“ (Issue #1946, Plan #1905 E8).
 */
class NacharbeitSignaleTest {

  @ParameterizedTest
  @CsvSource(
      nullValues = "-",
      value = {
        "IN_PROGRESS, READY, true",
        "IN_PROGRESS, BACKLOG, true",
        "IN_PROGRESS, DONE, false",
        "IN_PROGRESS, IN_REVIEW, false",
        "READY, BACKLOG, false",
        "-, READY, false",
        "IN_PROGRESS, -, false"
      })
  void rueckzugIstEineBewegungAusInProgressNachReadyOderBacklog(
      @Nullable String vorher, @Nullable String nachher, boolean rueckzug) {
    assertThat(NacharbeitSignale.istRueckzug(vorher, nachher)).isEqualTo(rueckzug);
  }

  @ParameterizedTest
  @ValueSource(strings = {"kit:klaeren", "kit:clarify", "lauf:wartet", "run:waiting"})
  void haltLabelsZaehlenInJederSprache(String label) {
    assertThat(NacharbeitSignale.istHalt(label)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"kit:protected", "run:running", "bug", ""})
  void andereLabelsSindKeinHalt(String label) {
    assertThat(NacharbeitSignale.istHalt(label)).isFalse();
  }
}
