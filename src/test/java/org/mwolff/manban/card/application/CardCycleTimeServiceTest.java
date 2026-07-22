package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardColumnRepository;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardRepository;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.board.domain.BoardColumn;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardColumnTransition;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;

/** Verhaltenstests der Dashboard-Aggregation (Ports gemockt, feste Uhr). */
class CardCycleTimeServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;
  private static final long WEEK = Duration.ofDays(7).toSeconds();
  private static final long THRESHOLD = Duration.ofDays(7).toSeconds();

  private CardRepository cards;
  private CardColumnTransitionRepository transitions;
  private BoardColumnRepository columns;
  private BoardRepository boards;
  private PermissionChecker permissions;
  private CardCycleTimeService service;

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    transitions = mock(CardColumnTransitionRepository.class);
    columns = mock(BoardColumnRepository.class);
    boards = mock(BoardRepository.class);
    permissions = mock(PermissionChecker.class);
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    service = new CardCycleTimeService(cards, transitions, columns, boards, permissions, clock);
    when(boards.findById(BOARD)).thenReturn(Optional.of(new Board(BOARD, PROJECT, "B", NOW)));
    stub(List.of(), List.of(), List.of());
  }

  private void stub(
      List<Card> boardCards, List<CardColumnTransition> trans, List<BoardColumn> cols) {
    when(cards.findByBoardId(BOARD)).thenReturn(boardCards);
    when(transitions.findByBoardId(BOARD)).thenReturn(trans);
    when(columns.findByBoardId(BOARD)).thenReturn(cols);
  }

  private static Card card(
      long id, long columnId, int number, String title, Instant createdAt, @Nullable Instant done) {
    return new Card(
        id,
        BOARD,
        columnId,
        number,
        title,
        null,
        0,
        false,
        false,
        done,
        1L,
        createdAt,
        createdAt,
        CardType.CARD,
        null,
        null,
        null,
        1L,
        null);
  }

  private static BoardColumn col(long id, String name, int position) {
    return new BoardColumn(id, BOARD, name, position, null);
  }

  private static CardColumnTransition tr(
      long cardId, long columnId, String name, Instant entered, @Nullable Long duration) {
    Instant left = duration == null ? null : entered.plusSeconds(duration);
    return new CardColumnTransition(1L, cardId, columnId, name, entered, left, duration);
  }

  @Test
  void dashboard_throwsBoardNotFound_whenBoardUnknown() {
    when(boards.findById(BOARD)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.dashboard(5L, BOARD))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void dashboard_requiresMembership_andReturnsEmptyKpis() {
    BoardDashboardKpis kpis = service.dashboard(5L, BOARD);

    verify(permissions).requireMembership(5L, PROJECT);
    assertThat(kpis.columnDwell()).isEmpty();
    assertThat(kpis.outliers()).isEmpty();
    assertThat(kpis.avgLeadTimeSeconds()).isNull();
    assertThat(kpis.avgCycleTimeSeconds()).isNull();
    assertThat(kpis.throughput()).hasSize(12);
    assertThat(kpis.throughput()).allSatisfy(w -> assertThat(w.doneCount()).isZero());
  }

  @Test
  void columnDwell_averagesClosedTransitionsPerColumn() {
    stub(
        List.of(card(1L, 21L, 1, "A", NOW, null)),
        List.of(
            tr(1L, 20L, "Backlog", NOW.minusSeconds(1000), 100L),
            tr(1L, 20L, "Backlog", NOW.minusSeconds(1000), 200L),
            tr(1L, 21L, "Done", NOW.minusSeconds(50), null)),
        List.of(col(20L, "Backlog", 0), col(21L, "Done", 1)));

    List<BoardDashboardKpis.ColumnDwell> dwell = service.dashboard(5L, BOARD).columnDwell();

    assertThat(dwell).hasSize(2);
    assertThat(dwell.get(0).columnId()).isEqualTo(20L);
    assertThat(dwell.get(0).avgDwellSeconds()).isEqualTo(150L);
    assertThat(dwell.get(0).sampleCount()).isEqualTo(2);
    // Nur offene Zeile -> keine abgeschlossene Verweildauer.
    assertThat(dwell.get(1).avgDwellSeconds()).isNull();
    assertThat(dwell.get(1).sampleCount()).isZero();
  }

  @Test
  void throughput_bucketsDoneCardsByWeekAndIgnoresOutOfWindow() {
    stub(
        List.of(
            card(1L, 20L, 1, "now", NOW.minusSeconds(10), NOW),
            card(2L, 20L, 2, "eleven", NOW.minusSeconds(10), NOW.minusSeconds(11 * WEEK + 3600)),
            card(3L, 20L, 3, "exactly12", NOW.minusSeconds(10), NOW.minusSeconds(12 * WEEK)),
            card(4L, 20L, 4, "old", NOW.minusSeconds(10), NOW.minusSeconds(13 * WEEK)),
            card(5L, 20L, 5, "future", NOW.minusSeconds(10), NOW.plusSeconds(86_400)),
            card(6L, 20L, 6, "open", NOW.minusSeconds(10), null)),
        List.of(),
        List.of());

    List<BoardDashboardKpis.WeeklyThroughput> weeks = service.dashboard(5L, BOARD).throughput();

    assertThat(weeks).hasSize(12);
    assertThat(weeks.get(11).doneCount()).isEqualTo(1); // "now" -> jüngstes Fenster
    assertThat(weeks.get(0).doneCount()).isEqualTo(1); // "eleven" -> ältestes Fenster
    assertThat(weeks.stream().mapToLong(BoardDashboardKpis.WeeklyThroughput::doneCount).sum())
        .isEqualTo(2); // exactly12/old/future/open zählen nicht
    assertThat(weeks.get(11).weekStart()).isEqualTo(NOW.minusSeconds(WEEK));
    assertThat(weeks.get(0).weekStart()).isEqualTo(NOW.minusSeconds(12 * WEEK));
  }

  @Test
  void leadTime_averagesCreatedToDoneOverDoneCards_ignoringEpics() {
    Card epic =
        new Card(
            9L,
            BOARD,
            20L,
            9,
            "epic",
            null,
            0,
            false,
            false,
            NOW,
            1L,
            NOW.minusSeconds(500_000),
            NOW.minusSeconds(500_000),
            CardType.EPIC,
            null,
            "EP",
            null,
            1L,
            null);
    stub(
        List.of(
            card(1L, 20L, 1, "a", NOW.minusSeconds(100), NOW),
            card(2L, 20L, 2, "b", NOW.minusSeconds(300), NOW),
            card(3L, 20L, 3, "open", NOW.minusSeconds(999), null),
            epic),
        List.of(),
        List.of());

    // Epic (Lead 500000) wird ausgefiltert -> Durchschnitt bleibt (100+300)/2.
    assertThat(service.dashboard(5L, BOARD).avgLeadTimeSeconds()).isEqualTo(200L);
  }

  @Test
  void cycleTime_averagesFromEarliestReadyEntryToDone() {
    Card done = card(1L, 21L, 1, "done", NOW.minusSeconds(9999), NOW);
    Card noReady = card(2L, 20L, 2, "noReady", NOW.minusSeconds(9999), NOW);
    Card openCard = card(3L, 21L, 3, "open", NOW.minusSeconds(9999), null);
    stub(
        List.of(done, noReady, openCard),
        List.of(
            tr(1L, 20L, "Backlog", NOW.minusSeconds(600), 100L),
            tr(1L, 21L, "Ready", NOW.minusSeconds(500), 500L),
            tr(1L, 21L, "Ready", NOW.minusSeconds(400), 400L),
            tr(2L, 20L, "Backlog", NOW.minusSeconds(700), 700L),
            tr(3L, 21L, "Ready", NOW.minusSeconds(300), null)),
        List.of(col(20L, "Backlog", 0), col(21L, "Ready", 1)));

    // Nur die abgeschlossene Karte mit Ready-Eintritt zählt; frühester Ready-Eintritt = -500s.
    assertThat(service.dashboard(5L, BOARD).avgCycleTimeSeconds()).isEqualTo(500L);
  }

  @Test
  void cycleTime_isNull_whenNoReadyColumnAmongDoneCards() {
    stub(
        List.of(card(1L, 20L, 1, "done", NOW.minusSeconds(9999), NOW)),
        List.of(tr(1L, 20L, "Backlog", NOW.minusSeconds(500), 500L)),
        List.of(col(20L, "Backlog", 0)));

    assertThat(service.dashboard(5L, BOARD).avgCycleTimeSeconds()).isNull();
  }

  @Test
  void outliers_reportTransitionsAboveThresholdSortedDescending() {
    Card longCard = card(1L, 20L, 5, "Long", NOW.minusSeconds(999_999), null);
    Card shortCard = card(2L, 20L, 6, "Short", NOW.minusSeconds(10), null);
    Card edgeCard = card(3L, 20L, 7, "Edge", NOW.minusSeconds(10), null);
    Card noTransitions = card(4L, 20L, 8, "None", NOW.minusSeconds(10), null);
    stub(
        List.of(longCard, shortCard, edgeCard, noTransitions),
        List.of(
            tr(1L, 20L, "Backlog", NOW.minusSeconds(999), 700_000L), // geschlossen, über Schwelle
            tr(1L, 21L, "Review", NOW.minusSeconds(800_000), null), // offen, über Schwelle
            tr(2L, 20L, "Backlog", NOW.minusSeconds(999), 100L), // unter Schwelle
            tr(3L, 20L, "Backlog", NOW.minusSeconds(999), THRESHOLD)), // exakt Schwelle -> raus
        List.of());

    List<BoardDashboardKpis.OutlierCard> out = service.dashboard(5L, BOARD).outliers();

    assertThat(out).hasSize(2);
    assertThat(out.get(0).dwellSeconds()).isEqualTo(800_000L);
    assertThat(out.get(0).columnName()).isEqualTo("Review");
    assertThat(out.get(0).number()).isEqualTo(5);
    assertThat(out.get(0).title()).isEqualTo("Long");
    assertThat(out.get(1).dwellSeconds()).isEqualTo(700_000L);
    assertThat(out.get(1).columnName()).isEqualTo("Backlog");
    assertThat(out).noneSatisfy(o -> assertThat(o.dwellSeconds()).isEqualTo(THRESHOLD));
  }
}
