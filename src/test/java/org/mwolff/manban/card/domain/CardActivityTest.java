package org.mwolff.manban.card.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Wert-Test für den {@link CardActivity}-Record. */
class CardActivityTest {

  @Test
  void carriesAllFields() {
    Instant at = Instant.parse("2026-01-01T10:00:00Z");
    CardActivity a = new CardActivity(1L, 42L, 9L, CardActivityType.MOVED, "Verschoben", at);

    assertThat(a.id()).isEqualTo(1L);
    assertThat(a.cardId()).isEqualTo(42L);
    assertThat(a.actorUserId()).isEqualTo(9L);
    assertThat(a.type()).isEqualTo(CardActivityType.MOVED);
    assertThat(a.detail()).isEqualTo("Verschoben");
    assertThat(a.createdAt()).isEqualTo(at);
  }
}
