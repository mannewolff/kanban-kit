package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;

/** Nummern board-fremder Vorfahren, nur aus dem eigenen Projekt (Issue #1982, Plan #1980 E4). */
class FremdeVorfahrenTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;

  private CardRepository cards;

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
  }

  private static Card karte(long id, long boardId, long projectId, int number, Long derivedFrom) {
    return new Card(
        id,
        boardId,
        20L,
        number,
        "Titel",
        null,
        0,
        false,
        null,
        1L,
        FIXED,
        FIXED,
        CardType.CARD,
        null,
        null,
        null,
        projectId,
        null,
        derivedFrom,
        null,
        null);
  }

  @Test
  void vorfahrAufAnderemBoardDesselbenProjekts_liefertSeineNummer() {
    when(cards.findByIds(Set.of(77L))).thenReturn(List.of(karte(77L, 99L, PROJECT, 42, null)));

    assertThat(FremdeVorfahren.nummern(cards, PROJECT, List.of(karte(6L, BOARD, PROJECT, 2, 77L))))
        .containsExactlyEntriesOf(Map.of(77L, 42));
  }

  /**
   * Der Betrachter muss im fremden Projekt nicht Mitglied sein: Dessen Nummer gehört nicht in den
   * Baum (Plan #1980, E4).
   */
  @Test
  void vorfahrInAnderemProjekt_liefertKeineNummer() {
    when(cards.findByIds(Set.of(77L))).thenReturn(List.of(karte(77L, 99L, 2L, 42, null)));

    assertThat(FremdeVorfahren.nummern(cards, PROJECT, List.of(karte(6L, BOARD, PROJECT, 2, 77L))))
        .isEmpty();
  }

  /** Ein Vorfahr auf demselben Board ist nicht fremd und wird nicht nachgeladen. */
  @Test
  void vorfahrAufDemBoard_wirdNichtNachgeladen() {
    List<Card> board =
        List.of(karte(5L, BOARD, PROJECT, 1, null), karte(6L, BOARD, PROJECT, 2, 5L));

    assertThat(FremdeVorfahren.nummern(cards, PROJECT, board)).isEmpty();
    verify(cards, never()).findByIds(any());
  }
}
