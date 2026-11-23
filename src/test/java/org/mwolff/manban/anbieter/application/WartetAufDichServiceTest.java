package org.mwolff.manban.anbieter.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.anbieter.application.WartetAufDichService.Grund;
import org.mwolff.manban.anbieter.application.WartetAufDichService.WartendeKarte;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardIngestService;
import org.mwolff.manban.card.application.CardIngestService.BoardItemView;
import org.mwolff.manban.card.application.LabelService;
import org.mwolff.manban.comment.application.RueckfrageService;
import org.mwolff.manban.comment.application.RueckfrageService.RueckfrageView;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung.RueckfrageZustand;
import org.mwolff.manban.project.application.BoardQuelle;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectNotFoundException;

/** Was auf den Kunden wartet (Issue #2216, Plan #2206 E14). */
class WartetAufDichServiceTest {

  private static final long KUNDE = 7L;
  private static final long PROJEKT = 1L;
  private static final long BOARD = 3L;
  private static final long ZWEITES_BOARD = 4L;
  private static final Instant AM = Instant.parse("2026-11-22T10:00:00Z");

  private PermissionChecker permissions;
  private BoardQuelle boardQuelle;
  private BoardService boards;
  private CardIngestService cards;
  private LabelService labels;
  private RueckfrageService rueckfragen;
  private WartetAufDichService service;

  @BeforeEach
  void setUp() {
    permissions = mock(PermissionChecker.class);
    boardQuelle = mock(BoardQuelle.class);
    boards = mock(BoardService.class);
    cards = mock(CardIngestService.class);
    labels = mock(LabelService.class);
    rueckfragen = mock(RueckfrageService.class);
    service =
        new WartetAufDichService(permissions, boardQuelle, boards, cards, labels, rueckfragen);
    when(boards.listActiveBoardIds(PROJEKT)).thenReturn(List.of(BOARD));
  }

  private static BoardItemView karte(long id) {
    return new BoardItemView(
        id, (int) id + 100, "Karte " + id, null, 1L, 0, false, null, null, null);
  }

  private static RueckfrageView rueckfrage(
      long id, RueckfrageZustand zustand, @Nullable String commit) {
    return new RueckfrageView(
        id, AM, zustand, commit == null ? null : "https://vorschau.example.org", commit, List.of());
  }

  private void aufDemBoard(long board, List<BoardItemView> karten, Map<Long, List<String>> namen) {
    when(cards.listBoardItems(KUNDE, board)).thenReturn(karten);
    when(labels.namesByCard(board, karten.stream().map(BoardItemView::id).toList()))
        .thenReturn(namen);
  }

  @Test
  void offeneRueckfrageUndFreigabeLabelErscheinenBeantworteteUndUeberholteNicht() {
    // Given: 10 offene Rückfrage, 11 kit:approval, 12 beantwortet, 13 überholte Freigabe mit
    // Label, 14 nichts, 15 offene Freigabe-Rückfrage
    aufDemBoard(
        BOARD,
        List.of(karte(10), karte(11), karte(12), karte(13), karte(14), karte(15)),
        Map.of(11L, List.of("bug", "kit:approval"), 13L, List.of("kit:approval")));
    when(rueckfragen.staendeJeKarte(KUNDE, PROJEKT, List.of(10L, 11L, 12L, 13L, 14L, 15L)))
        .thenReturn(
            Map.of(
                10L, List.of(rueckfrage(1, RueckfrageZustand.OFFEN, null)),
                12L, List.of(rueckfrage(2, RueckfrageZustand.BEANTWORTET, null)),
                13L, List.of(rueckfrage(3, RueckfrageZustand.UEBERHOLT, "abc")),
                15L, List.of(rueckfrage(4, RueckfrageZustand.OFFEN, "abc"))));

    // When
    List<WartendeKarte> wartend = service.liste(KUNDE, PROJEKT);

    // Then
    assertThat(wartend)
        .containsExactly(
            new WartendeKarte(10L, BOARD, 110, "Karte 10", Grund.RUECKFRAGE),
            new WartendeKarte(11L, BOARD, 111, "Karte 11", Grund.FREIGABE),
            new WartendeKarte(15L, BOARD, 115, "Karte 15", Grund.FREIGABE));
  }

  @Test
  void freigabeLabelZaehltNichtNachBeantworteterFreigabe() {
    // When / Then
    assertThat(
            WartetAufDichService.grund(
                List.of("kit:approval"),
                List.of(rueckfrage(1, RueckfrageZustand.BEANTWORTET, "abc"))))
        .isEmpty();
  }

  @Test
  void freigabeLabelZaehltNebenEinerBeantwortetenGewoehnlichenRueckfrage() {
    // When / Then
    assertThat(
            WartetAufDichService.grund(
                List.of("kit:approval"),
                List.of(rueckfrage(1, RueckfrageZustand.BEANTWORTET, null))))
        .contains(Grund.FREIGABE);
  }

  @Test
  void andereLabelsOhneRueckfrageWartenNicht() {
    // When / Then: ein fremdes und ein Kit-Label anderer Sache sind keine Freigabe (Issue #2236)
    assertThat(WartetAufDichService.grund(List.of("bug", "kit:clarify"), List.of())).isEmpty();
  }

  @Test
  void offeneGewoehnlicheRueckfrageGehtVorFreigabe() {
    // When / Then
    assertThat(
            WartetAufDichService.grund(
                List.of("kit:approval"),
                List.of(
                    rueckfrage(1, RueckfrageZustand.OFFEN, "abc"),
                    rueckfrage(2, RueckfrageZustand.OFFEN, null))))
        .contains(Grund.RUECKFRAGE);
  }

  @Test
  void alleBoardsDesProjektsInIhrerReihenfolge() {
    // Given
    when(boards.listActiveBoardIds(PROJEKT)).thenReturn(List.of(BOARD, ZWEITES_BOARD));
    aufDemBoard(BOARD, List.of(karte(10)), Map.of(10L, List.of("kit:approval")));
    aufDemBoard(ZWEITES_BOARD, List.of(karte(20)), Map.of());
    when(rueckfragen.staendeJeKarte(KUNDE, PROJEKT, List.of(10L, 20L)))
        .thenReturn(Map.of(20L, List.of(rueckfrage(1, RueckfrageZustand.OFFEN, null))));

    // When / Then
    assertThat(service.liste(KUNDE, PROJEKT))
        .extracting(WartendeKarte::cardId, WartendeKarte::boardId)
        .containsExactly(tuple(10L, BOARD), tuple(20L, ZWEITES_BOARD));
  }

  @Test
  void fremdesBoardLiefertNichts() {
    // Given
    when(boardQuelle.istFremd(PROJEKT)).thenReturn(true);

    // When / Then
    assertThat(service.liste(KUNDE, PROJEKT)).isEmpty();
    verifyNoInteractions(cards, rueckfragen);
  }

  @Test
  void nichtmitgliedEndetMit404() {
    // Given
    doThrow(new ProjectNotFoundException()).when(permissions).requireMembership(KUNDE, PROJEKT);

    // When / Then
    assertThatThrownBy(() -> service.liste(KUNDE, PROJEKT))
        .isInstanceOf(ProjectNotFoundException.class);
    verifyNoInteractions(cards, rueckfragen);
  }
}
