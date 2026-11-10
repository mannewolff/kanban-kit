package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Ob ein Paket eine Mail des Laufs auslöst: dieselbe Abgrenzung wie {@link Meldegrund#anlaesse}, je
 * Paket (Issue #2078, Plan #2070 E3).
 */
class MeldegrundLoestAnlassAusTest {

  private static NightRunItem paket(
      NightRunKind art, NightRunState state, @Nullable NightRunErrorClass errorClass) {
    return new NightRunItem(
        10L,
        1L,
        7L,
        Instant.parse("2026-10-28T22:00:00Z"),
        NightRunMode.IMPLEMENTATION,
        art,
        1,
        "Paket",
        state,
        errorClass,
        1_000L,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        List.of());
  }

  @ParameterizedTest
  @CsvSource({
    "RED, AWAITING_DECISION",
    "GREY, AWAITING_DECISION",
    "RED, CHECKS_RED",
    "RED, TIME_BUDGET_EXCEEDED",
    "YELLOW, TIME_BUDGET_EXCEEDED",
    "GREY, STUCK"
  })
  void einPaketMitAnlassLoestEineMailDesLaufsAus(
      NightRunState state, NightRunErrorClass errorClass) {
    assertThat(Meldegrund.loestAnlassAus(paket(NightRunKind.NIGHT, state, errorClass))).isTrue();
  }

  @ParameterizedTest
  @CsvSource({"GREEN,", "GREY,", "GREY, DEPENDENCY_UNMET", "YELLOW, CHECKS_RED"})
  void einPaketOhneAnlassLoestKeineMailDesLaufsAus(
      NightRunState state, @Nullable NightRunErrorClass errorClass) {
    assertThat(Meldegrund.loestAnlassAus(paket(NightRunKind.NIGHT, state, errorClass))).isFalse();
  }

  @Test
  void einPaketEinerInteraktivenSitzungLoestKeineMailDesLaufsAus() {
    NightRunItem paket =
        paket(NightRunKind.INTERACTIVE, NightRunState.RED, NightRunErrorClass.AWAITING_DECISION);

    assertThat(Meldegrund.loestAnlassAus(paket)).isFalse();
  }
}
