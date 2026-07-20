package org.mwolff.manban.card.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Zustands-Wither des {@link Card}-Records rund um den Ideen-Speicher. */
class CardTest {

  private static final Instant FIXED = Instant.parse("2026-01-01T00:00:00Z");

  private static final Card CARD =
      new Card(
          1L,
          10L,
          20L,
          5,
          "T",
          "desc",
          3,
          false,
          false,
          null,
          1L,
          FIXED,
          FIXED,
          CardType.CARD,
          null,
          null,
          null);

  @Test
  void asIdeaStored_setztNurDasFlag() {
    Card result = CARD.asIdeaStored();

    assertThat(result.ideaStored()).isTrue();
    assertThat(result).usingRecursiveComparison().ignoringFields("ideaStored").isEqualTo(CARD);
  }

  @Test
  void asPromoted_loeschtFlag_setztSpalteUndPosition_haeltRest() {
    Card idea = CARD.asIdeaStored();

    Card result = idea.asPromoted(7, 99L);

    assertThat(result.ideaStored()).isFalse();
    assertThat(result.columnId()).isEqualTo(99L);
    assertThat(result.positionInColumn()).isEqualTo(7);
    assertThat(result)
        .usingRecursiveComparison()
        .ignoringFields("ideaStored", "columnId", "positionInColumn")
        .isEqualTo(idea);
  }
}
