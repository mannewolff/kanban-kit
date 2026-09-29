package org.mwolff.manban.card.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Zustands-Wither des {@link Card}-Records. */
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
          null,
          null,
          null);

  @Test
  void karteTraegtBoardSpalteUndNummer() {
    assertThat(CARD.boardId()).isEqualTo(10L);
    assertThat(CARD.columnId()).isEqualTo(20L);
    assertThat(CARD.number()).isEqualTo(5);
  }

  /**
   * Die Herkunft muss durch <strong>jede</strong> Konstruktionsstelle des Records wandern. Der
   * Compiler erzwingt, dass jede Stelle angefasst wird — aber nicht, dass sie den Wert
   * weiterreicht: Ein durchgereichtes {@code null} kompiliert anstandslos und faellt erst spaeter
   * als Datenverlust auf. Deshalb wird hier jede Stelle einzeln durchlaufen.
   */
  @Test
  void derivedFrom_ueberlebt_jedeKonstruktionsstelleDesRecords() {
    Card mitHerkunft = CARD.withDerivedFrom(99L);

    assertThat(mitHerkunft.withContent("neu", "d").derivedFromCardId()).isEqualTo(99L);
    assertThat(mitHerkunft.asArchived().derivedFromCardId()).isEqualTo(99L);
    assertThat(mitHerkunft.asArchived().asRestored(0).derivedFromCardId()).isEqualTo(99L);
    assertThat(mitHerkunft.withMovedToDoneAt(FIXED).derivedFromCardId()).isEqualTo(99L);
    assertThat(mitHerkunft.withParent(7L).derivedFromCardId()).isEqualTo(99L);
    assertThat(mitHerkunft.withShortcode("K").derivedFromCardId()).isEqualTo(99L);
    assertThat(mitHerkunft.withDueDate(FIXED).derivedFromCardId()).isEqualTo(99L);
    assertThat(mitHerkunft.withRequirement(3L).derivedFromCardId()).isEqualTo(99L);
  }

  @Test
  void withDerivedFrom_setztUndLoescht() {
    assertThat(CARD.derivedFromCardId()).isNull();
    assertThat(CARD.withDerivedFrom(99L).derivedFromCardId()).isEqualTo(99L);
    assertThat(CARD.withDerivedFrom(99L).withDerivedFrom(null).derivedFromCardId()).isNull();
  }

  /** Wie die Herkunft muss auch der Status durch jede Konstruktionsstelle wandern. */
  @Test
  void status_ueberlebt_jedeKonstruktionsstelleDesRecords() {
    Card mitStatus = CARD.withStatus(CardStatus.READY);

    assertThat(mitStatus.withContent("neu", "d").status()).isEqualTo(CardStatus.READY);
    assertThat(mitStatus.asArchived().status()).isEqualTo(CardStatus.READY);
    assertThat(mitStatus.asArchived().asRestored(0).status()).isEqualTo(CardStatus.READY);
    assertThat(mitStatus.withMovedToDoneAt(FIXED).status()).isEqualTo(CardStatus.READY);
    assertThat(mitStatus.withParent(7L).status()).isEqualTo(CardStatus.READY);
    assertThat(mitStatus.withShortcode("K").status()).isEqualTo(CardStatus.READY);
    assertThat(mitStatus.withDueDate(FIXED).status()).isEqualTo(CardStatus.READY);
    assertThat(mitStatus.withRequirement(3L).status()).isEqualTo(CardStatus.READY);
    assertThat(mitStatus.withDerivedFrom(9L).status()).isEqualTo(CardStatus.READY);
  }

  @Test
  void withStatus_setztUndLoescht() {
    assertThat(CARD.status()).isNull();
    assertThat(CARD.withStatus(CardStatus.DONE).status()).isEqualTo(CardStatus.DONE);
    assertThat(CARD.withStatus(CardStatus.DONE).withStatus(null).status()).isNull();
  }
}
