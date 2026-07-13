package org.mwolff.manban.card.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Wert-Test für den {@link CardColumnTransition}-Record (offene und geschlossene Zeile). */
class CardColumnTransitionTest {

  private static final Instant ENTERED = Instant.parse("2026-01-01T10:00:00Z");
  private static final Instant LEFT = Instant.parse("2026-01-01T10:01:30Z");

  @Test
  void openTransitionCarriesFieldsAndLeavesExitNull() {
    CardColumnTransition open = new CardColumnTransition(1L, 42L, 7L, "Ready", ENTERED, null, null);

    assertThat(open.id()).isEqualTo(1L);
    assertThat(open.cardId()).isEqualTo(42L);
    assertThat(open.columnId()).isEqualTo(7L);
    assertThat(open.columnName()).isEqualTo("Ready");
    assertThat(open.enteredAt()).isEqualTo(ENTERED);
    assertThat(open.leftAt()).isNull();
    assertThat(open.durationSeconds()).isNull();
  }

  @Test
  void closedTransitionCarriesExitAndDuration() {
    CardColumnTransition closed =
        new CardColumnTransition(2L, 42L, 7L, "Ready", ENTERED, LEFT, 90L);

    assertThat(closed.leftAt()).isEqualTo(LEFT);
    assertThat(closed.durationSeconds()).isEqualTo(90L);
  }
}
