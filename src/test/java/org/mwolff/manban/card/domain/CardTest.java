package org.mwolff.manban.card.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
          null,
          1L,
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

  @Test
  void requireBoardColumnNumber_liefernDieWerte_beiBoardgebundenerKarte() {
    assertThat(CARD.requireBoardId()).isEqualTo(10L);
    assertThat(CARD.requireColumnId()).isEqualTo(20L);
    assertThat(CARD.requireNumber()).isEqualTo(5);
  }

  @Test
  void asPooledIdea_entferntBoardSpalteNummer_setztFlagUndZielboard() {
    Card idea = CARD.asPooledIdea(42L);

    assertThat(idea.ideaStored()).isTrue();
    assertThat(idea.boardId()).isNull();
    assertThat(idea.columnId()).isNull();
    assertThat(idea.number()).isNull();
    assertThat(idea.targetBoardId()).isEqualTo(42L);
    assertThat(idea.projectId()).isEqualTo(1L);
  }

  @Test
  void requireBoardColumnNumber_werfen_beiBoardloserPoolIdee() {
    Card idea = CARD.asPooledIdea(null);

    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(idea::requireBoardId);
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(idea::requireColumnId);
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(idea::requireNumber);
  }

  @Test
  void withPlannedOnBoard_setztBoardSpalteNummerPosition_loeschtFlagUndZielboard() {
    Card idea = CARD.asPooledIdea(42L);

    Card planned = idea.withPlannedOnBoard(70L, 80L, 9, 3);

    assertThat(planned.ideaStored()).isFalse();
    assertThat(planned.boardId()).isEqualTo(70L);
    assertThat(planned.columnId()).isEqualTo(80L);
    assertThat(planned.number()).isEqualTo(9);
    assertThat(planned.positionInColumn()).isEqualTo(3);
    assertThat(planned.targetBoardId()).isNull();
    assertThat(planned.projectId()).isEqualTo(1L);
  }
}
