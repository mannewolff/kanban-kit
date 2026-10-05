package org.mwolff.manban.card.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Wert-Test für den {@link CardActivity}-Record. */
class CardActivityTest {

  @Test
  void carriesAllFields() {
    Instant at = Instant.parse("2026-01-01T10:00:00Z");
    CardActivity a =
        new CardActivity(
            1L,
            42L,
            9L,
            CardActivityType.MOVED,
            "Verschoben",
            at,
            CardActivityOrigin.TOKEN,
            "Nachtlauf",
            "claude-opus-5",
            Instant.parse("2026-01-01T09:00:00.123Z"),
            CardStatus.IN_REVIEW);

    assertThat(a.id()).isEqualTo(1L);
    assertThat(a.cardId()).isEqualTo(42L);
    assertThat(a.actorUserId()).isEqualTo(9L);
    assertThat(a.type()).isEqualTo(CardActivityType.MOVED);
    assertThat(a.detail()).isEqualTo("Verschoben");
    assertThat(a.createdAt()).isEqualTo(at);
    assertThat(a.origin()).isEqualTo(CardActivityOrigin.TOKEN);
    assertThat(a.tokenName()).isEqualTo("Nachtlauf");
    assertThat(a.agent()).isEqualTo("claude-opus-5");
    assertThat(a.laufStart()).isEqualTo(Instant.parse("2026-01-01T09:00:00.123Z"));
    assertThat(a.statusAfter()).isEqualTo(CardStatus.IN_REVIEW);
  }

  @Test
  void carriesEmptyOrigin_forLegacyEntries() {
    // Alt-Einträge vor V23 bzw. V47: keine Herkunft rekonstruierbar — alle Felder null.
    Instant at = Instant.parse("2026-01-01T10:00:00Z");
    CardActivity a =
        new CardActivity(
            1L, 42L, 9L, CardActivityType.MOVED, "Verschoben", at, null, null, null, null, null);

    assertThat(a.origin()).isNull();
    assertThat(a.tokenName()).isNull();
    assertThat(a.agent()).isNull();
    assertThat(a.laufStart()).isNull();
    assertThat(a.statusAfter()).isNull();
  }
}
