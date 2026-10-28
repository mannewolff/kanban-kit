package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Ein Vorgang einer Stufe, wie der Runner ihn meldet (Issue #1944, Plan #1905 E1, E6). */
class NightRunAttemptTest {

  @Test
  void fehlendeGeschriebeneKartenWerdenZurLeerenListe() {
    NightRunAttempt vorgang =
        new NightRunAttempt(NightRunAttemptStage.PLAN, 1904, null, null, true, null, null);

    assertThat(vorgang.writtenCards()).isEmpty();
  }

  /**
   * Die Liste ist eine unveränderliche Kopie: Ein Aufrufer, der seine Liste danach weiterfüllt,
   * änderte sonst einen gespeicherten Vorgang nachträglich.
   */
  @Test
  void dieGeschriebenenKartenSindEineUnveraenderlicheKopieInIhrerReihenfolge() {
    List<Integer> veraenderlich = new ArrayList<>(List.of(1905, 1944));
    NightRunAttempt vorgang =
        new NightRunAttempt(
            NightRunAttemptStage.PAKETE,
            1905,
            "claude-opus-5-5",
            "high",
            true,
            null,
            veraenderlich);

    veraenderlich.clear();

    assertThat(vorgang.writtenCards()).containsExactly(1905, 1944);
    assertThat(vorgang.writtenCards()).isUnmodifiable();
  }
}
