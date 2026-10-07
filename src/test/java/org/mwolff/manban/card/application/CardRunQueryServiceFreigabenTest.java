package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardSummary;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.CardRunQueryService.FreigabeKarteView;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.card.domain.Label;
import org.mwolff.manban.project.application.PermissionChecker;

/**
 * Unit-Tests der freigegebenen Karten eines Projekts für die Übersicht „Heute Nacht“ (Issue #1454).
 *
 * <p>Eigene Klasse wie {@code CardRunQueryServiceLaufspurenTest}. Als Unit-Test, weil PIT allein
 * Unit-Tests misst.
 */
class CardRunQueryServiceFreigabenTest {

  private static final Instant FIXED = Instant.parse("2026-10-05T15:00:00Z");
  private static final long PROJECT = 1L;
  private static final long BOARD = 2L;
  private static final long ARCHIV = 3L;
  private static final String NACHT = "kit:night";
  private static final long SPALTE_BACKLOG = 10L;
  private static final long SPALTE_DONE = 11L;
  private static final long SPALTE_EIGEN = 12L;

  private CardRepository cards;
  private LabelRepository labels;
  private CardLabelRepository cardLabels;
  private BoardService boards;
  private CardRunQueryService service;

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    labels = mock(LabelRepository.class);
    cardLabels = mock(CardLabelRepository.class);
    boards = mock(BoardService.class);
    service =
        new CardRunQueryService(
            cards,
            mock(CardActivityRepository.class),
            new KartenZuordnung(
                mock(CardAssigneeRepository.class),
                labels,
                cardLabels,
                mock(PermissionChecker.class)),
            boards);
    when(boards.requireBoardSummary(BOARD))
        .thenReturn(new BoardSummary(BOARD, "Entwicklung", false));
    when(boards.requireBoardSummary(ARCHIV)).thenReturn(new BoardSummary(ARCHIV, "Alt", true));
    when(boards.listColumns(BOARD))
        .thenReturn(
            List.of(
                new ColumnView(SPALTE_BACKLOG, "Backlog", 0, null),
                new ColumnView(SPALTE_DONE, "Done", 1, null),
                new ColumnView(SPALTE_EIGEN, "Wartet", 2, null)));
    when(labels.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                new Label(20L, BOARD, NACHT, "#000", false),
                new Label(21L, BOARD, "ziel:umsetzung", "#000", false)));
    when(labels.findByBoardId(ARCHIV))
        .thenReturn(List.of(new Label(30L, ARCHIV, NACHT, "#000", false)));
  }

  private static Card karte(long id, long board, String titel) {
    return karte(id, board, titel, SPALTE_BACKLOG, false, null);
  }

  private static Card karte(
      long id,
      long board,
      String titel,
      long spalte,
      boolean archiviert,
      @Nullable CardStatus status) {
    return new Card(
        id,
        board,
        spalte,
        (int) id + 100,
        titel,
        null,
        0,
        archiviert,
        null,
        null,
        FIXED,
        FIXED,
        CardType.CARD,
        null,
        null,
        null,
        PROJECT,
        null,
        null,
        null,
        status);
  }

  @Test
  void liefertDieKartenMitLabelUndPassendemTitelSamtBoardnamenUndLabels() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(List.of(karte(5L, BOARD, "[Fachlich] A"), karte(6L, BOARD, "[Fachlich] B")));
    when(cardLabels.findByCardIds(List.of(5L, 6L)))
        .thenReturn(Map.of(5L, List.of(20L, 21L), 6L, List.of(21L)));

    assertThat(service.freigegebeneKarten(PROJECT, NACHT, t -> t.startsWith("[Fachlich]")))
        .containsExactly(
            new FreigabeKarteView(
                105, "[Fachlich] A", "Entwicklung", List.of(NACHT, "ziel:umsetzung")));
  }

  @Test
  void eineKarteMitUnpassendemTitelWirdNichtNachLabelsGefragt() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(List.of(karte(5L, BOARD, "Export"), karte(6L, BOARD, "[Plan] B")));
    when(cardLabels.findByCardIds(List.of(6L))).thenReturn(Map.of(6L, List.of(20L)));

    assertThat(service.freigegebeneKarten(PROJECT, NACHT, t -> t.startsWith("[Plan]")))
        .containsExactly(new FreigabeKarteView(106, "[Plan] B", "Entwicklung", List.of(NACHT)));
  }

  @Test
  void kartenEinesArchiviertenBoardsFehlen() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(List.of(karte(5L, ARCHIV, "[Plan] A"), karte(6L, BOARD, "[Plan] B")));
    when(cardLabels.findByCardIds(List.of(6L))).thenReturn(Map.of(6L, List.of(20L)));

    assertThat(service.freigegebeneKarten(PROJECT, NACHT, t -> true))
        .extracting(FreigabeKarteView::number)
        .containsExactly(106);
  }

  @Test
  void dieSpaltenEinesArchiviertenBoardsWerdenNichtGelesen() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(List.of(karte(5L, ARCHIV, "[Plan] A"), karte(6L, BOARD, "[Plan] B")));
    when(cardLabels.findByCardIds(List.of(6L))).thenReturn(Map.of(6L, List.of(20L)));

    assertThat(service.freigegebeneKarten(PROJECT, NACHT, t -> true))
        .extracting(FreigabeKarteView::number)
        .containsExactly(106);
    verify(boards, never()).listColumns(ARCHIV);
    verify(boards).listColumns(BOARD);
  }

  @Test
  void eineArchivierteKarteFehlt() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(
            List.of(
                karte(5L, BOARD, "[Fachlich] A", SPALTE_BACKLOG, true, null),
                karte(6L, BOARD, "[Fachlich] B")));
    when(cardLabels.findByCardIds(List.of(6L))).thenReturn(Map.of(6L, List.of(20L)));

    assertThat(service.freigegebeneKarten(PROJECT, NACHT, t -> true))
        .extracting(FreigabeKarteView::number)
        .containsExactly(106);
  }

  @Test
  void eineKarteMitStatusDoneFehltAuchInDerBacklogSpalte() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(
            List.of(
                karte(5L, BOARD, "Paket", SPALTE_BACKLOG, false, CardStatus.DONE),
                karte(6L, BOARD, "Paket", SPALTE_DONE, false, CardStatus.BACKLOG)));
    when(cardLabels.findByCardIds(List.of(6L))).thenReturn(Map.of(6L, List.of(20L)));

    assertThat(service.freigegebeneKarten(PROJECT, NACHT, t -> true))
        .extracting(FreigabeKarteView::number)
        .containsExactly(106);
  }

  @Test
  void eineKarteOhneStatusInDerSpalteDoneFehlt() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(
            List.of(
                karte(5L, BOARD, "[Fachlich] A", SPALTE_DONE, false, null),
                karte(6L, BOARD, "[Fachlich] B")));
    when(cardLabels.findByCardIds(List.of(6L))).thenReturn(Map.of(6L, List.of(20L)));

    assertThat(service.freigegebeneKarten(PROJECT, NACHT, t -> true))
        .extracting(FreigabeKarteView::number)
        .containsExactly(106);
  }

  @Test
  void eineKarteOhneStatusInEinerEigenenSpalteGiltAlsBacklog() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(List.of(karte(5L, BOARD, "[Plan] A", SPALTE_EIGEN, false, null)));
    when(cardLabels.findByCardIds(List.of(5L))).thenReturn(Map.of(5L, List.of(20L)));

    assertThat(service.freigegebeneKarten(PROJECT, NACHT, t -> true))
        .extracting(FreigabeKarteView::number)
        .containsExactly(105);
  }

  @Test
  void ohneKarteIstDasErgebnisLeer() {
    when(cards.findByProjectId(PROJECT)).thenReturn(List.of());

    assertThat(service.freigegebeneKarten(PROJECT, NACHT, t -> true)).isEmpty();
  }
}
