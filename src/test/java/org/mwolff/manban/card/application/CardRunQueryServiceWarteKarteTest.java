package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.CardRunQueryService.WarteKarteView;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;

/**
 * Die Karte, deren Wartegrund gelesen wird (Issue #1728, Plan #1724 E12): Ready heißt, der
 * Kanban-Schlüssel ist READY — der eigene Status vor der Spalte.
 */
class CardRunQueryServiceWarteKarteTest {

  private static final Instant GEAENDERT = Instant.parse("2026-10-14T01:00:00Z");
  private static final long CARD = 5L;
  private static final long BOARD = 2L;
  private static final long SPALTE_READY = 10L;
  private static final long SPALTE_BACKLOG = 11L;

  private CardRepository cards;
  private BoardService boards;
  private CardRunQueryService service;

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    boards = mock(BoardService.class);
    service =
        new CardRunQueryService(
            cards,
            mock(CardActivityRepository.class),
            new KartenZuordnung(
                mock(CardAssigneeRepository.class),
                mock(LabelRepository.class),
                mock(CardLabelRepository.class),
                mock(PermissionChecker.class)),
            boards);
    when(boards.requireColumn(SPALTE_READY, BOARD))
        .thenReturn(new ColumnView(SPALTE_READY, "Ready", 1, null));
    when(boards.requireColumn(SPALTE_BACKLOG, BOARD))
        .thenReturn(new ColumnView(SPALTE_BACKLOG, "Backlog", 0, null));
  }

  private static Card karte(long spalte, @Nullable CardStatus status, boolean archiviert) {
    return new Card(
        CARD,
        BOARD,
        spalte,
        1695,
        "Paket",
        null,
        0,
        archiviert,
        null,
        null,
        GEAENDERT.minusSeconds(3600),
        GEAENDERT,
        CardType.CARD,
        null,
        null,
        null,
        7L,
        null,
        null,
        null,
        status);
  }

  @Test
  void eineKarteInDerSpalteReady_ohneEigenenStatus_istReady() {
    when(cards.findByIdAndDeletedAtIsNull(CARD))
        .thenReturn(Optional.of(karte(SPALTE_READY, null, false)));

    assertThat(service.warteKarte(CARD)).isEqualTo(new WarteKarteView(7L, 1695, true, GEAENDERT));
  }

  @Test
  void derEigeneStatusGehtVorDerSpalte() {
    when(cards.findByIdAndDeletedAtIsNull(CARD))
        .thenReturn(Optional.of(karte(SPALTE_BACKLOG, CardStatus.READY, false)));

    assertThat(service.warteKarte(CARD).ready()).isTrue();
  }

  @Test
  void einStatusBacklogInDerSpalteReady_istNichtReady() {
    when(cards.findByIdAndDeletedAtIsNull(CARD))
        .thenReturn(Optional.of(karte(SPALTE_READY, CardStatus.BACKLOG, false)));

    assertThat(service.warteKarte(CARD).ready()).isFalse();
  }

  /** Eine archivierte Karte sieht der Runner nicht — sie wartet nicht. */
  @Test
  void eineArchivierteKarte_istNichtReady() {
    when(cards.findByIdAndDeletedAtIsNull(CARD))
        .thenReturn(Optional.of(karte(SPALTE_READY, CardStatus.READY, true)));

    assertThat(service.warteKarte(CARD).ready()).isFalse();
  }

  /** Eine unbekannte Karte und eine im Papierkorb sind dasselbe: nicht gefunden. */
  @Test
  void eineKarteImPapierkorbOderUnbekannt_wirftNichtGefunden() {
    when(cards.findByIdAndDeletedAtIsNull(CARD)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.warteKarte(CARD)).isInstanceOf(CardNotFoundException.class);
  }
}
