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
          null,
          null);

  @Test
  void requireBoardColumnNumber_liefernDieWerte_beiBoardgebundenerKarte() {
    assertThat(CARD.requireBoardId()).isEqualTo(10L);
    assertThat(CARD.requireColumnId()).isEqualTo(20L);
    assertThat(CARD.requireNumber()).isEqualTo(5);
  }

  @Test
  void asPooledIdea_entferntBoardUndSpalte_behaeltNummer_setztFlagUndZielboard() {
    Card idea = CARD.asPooledIdea(42L);

    assertThat(idea.ideaStored()).isTrue();
    assertThat(idea.boardId()).isNull();
    assertThat(idea.columnId()).isNull();
    // Issue #433: die projektweite Nummer bleibt erhalten (vorher wurde sie genullt) — der Weg in
    // den Ideen-Speicher soll Rückverweise (#N) nicht mehr brechen.
    assertThat(idea.number()).isEqualTo(5);
    assertThat(idea.targetBoardId()).isEqualTo(42L);
    assertThat(idea.projectId()).isEqualTo(1L);
  }

  @Test
  void requireBoardColumn_werfen_beiBoardloserPoolIdee() {
    // Seit #433 behält asPooledIdea die Nummer — requireBoardId/requireColumnId werfen weiterhin,
    // requireNumber nicht mehr (eigener Test unten für eine echt nummernlose Karte).
    Card idea = CARD.asPooledIdea(null);

    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(idea::requireBoardId);
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(idea::requireColumnId);
    assertThat(idea.requireNumber()).isEqualTo(5);
  }

  @Test
  void requireNumber_wirft_beiEchtNummernloserKarte() {
    // Nur Alt-Ideen aus der Zeit vor #402 haben number == null; requireNumber bleibt für diesen
    // Fall zuständig (planOntoBoard prüft genau darauf, bevor es eine Nummer nachvergibt).
    Card numberless =
        new Card(
            1L,
            null,
            null,
            null,
            "T",
            "desc",
            3,
            false,
            true,
            null,
            1L,
            FIXED,
            FIXED,
            CardType.CARD,
            null,
            null,
            null,
            1L,
            null,
            null);

    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(numberless::requireNumber);
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
