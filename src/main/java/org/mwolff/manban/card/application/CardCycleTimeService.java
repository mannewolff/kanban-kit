package org.mwolff.manban.card.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.BoardColumnRepository;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardRepository;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.board.domain.BoardColumn;
import org.mwolff.manban.card.application.BoardDashboardKpis.ColumnDwell;
import org.mwolff.manban.card.application.BoardDashboardKpis.OutlierCard;
import org.mwolff.manban.card.application.BoardDashboardKpis.WeeklyThroughput;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardColumnTransition;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Berechnet die Dashboard-Kennzahlen eines Boards aus der Spaltenaufenthalts-Historie ({@link
 * CardColumnTransitionRepository}) und den Karten-Zeitstempeln. Alle Aggregationen laufen in Java
 * über die je Board geladenen Daten — die Persistenz liefert nur die Rohzeilen.
 */
@Service
public class CardCycleTimeService {

  /** Ab dieser Verweildauer in einer Spalte gilt eine Karte als Ausreißer (7 Tage). */
  private static final long OUTLIER_THRESHOLD_SECONDS = Duration.ofDays(7).toSeconds();

  private static final int THROUGHPUT_WEEKS = 12;
  private static final long WEEK_SECONDS = Duration.ofDays(7).toSeconds();

  private final CardRepository cards;
  private final CardColumnTransitionRepository transitions;
  private final BoardColumnRepository columns;
  private final BoardRepository boards;
  private final PermissionChecker permissions;
  private final Clock clock;

  public CardCycleTimeService(
      CardRepository cards,
      CardColumnTransitionRepository transitions,
      BoardColumnRepository columns,
      BoardRepository boards,
      PermissionChecker permissions,
      Clock clock) {
    this.cards = cards;
    this.transitions = transitions;
    this.columns = columns;
    this.boards = boards;
    this.permissions = permissions;
    this.clock = clock;
  }

  /**
   * Dashboard-Kennzahlen eines Boards. Erfordert Board-Leserecht (Mitglied und aufwärts, wie die
   * Board-Ansicht).
   */
  @Transactional(readOnly = true)
  public BoardDashboardKpis dashboard(long userId, long boardId) {
    Board board = boards.findById(boardId).orElseThrow(BoardNotFoundException::new);
    permissions.requireMembership(userId, board.projectId());

    List<Card> workCards =
        cards.findByBoardId(boardId).stream().filter(c -> c.type() == CardType.CARD).toList();
    List<CardColumnTransition> trans = transitions.findByBoardId(boardId);
    List<BoardColumn> boardColumns = columns.findByBoardId(boardId);
    Instant now = clock.instant();

    Map<Long, List<CardColumnTransition>> byCard =
        trans.stream().collect(Collectors.groupingBy(CardColumnTransition::cardId));

    return new BoardDashboardKpis(
        columnDwell(boardColumns, trans),
        throughput(workCards, now),
        avgLeadTime(workCards),
        avgCycleTime(workCards, byCard),
        outliers(workCards, byCard, now));
  }

  private static List<ColumnDwell> columnDwell(
      List<BoardColumn> boardColumns, List<CardColumnTransition> trans) {
    return boardColumns.stream()
        .map(
            col -> {
              long[] durations =
                  trans.stream()
                      .filter(t -> Objects.equals(t.columnId(), col.id()))
                      .map(CardColumnTransition::durationSeconds)
                      .filter(Objects::nonNull)
                      .mapToLong(Long::longValue)
                      .toArray();
              return new ColumnDwell(
                  col.requireId(), col.name(), average(durations), durations.length);
            })
        .toList();
  }

  private static List<WeeklyThroughput> throughput(List<Card> workCards, Instant now) {
    long[] counts = new long[THROUGHPUT_WEEKS];
    for (Card card : workCards) {
      Instant done = card.movedToDoneAt();
      if (done == null) {
        continue;
      }
      long secondsAgo = Duration.between(done, now).toSeconds();
      long weeksAgo = secondsAgo / WEEK_SECONDS;
      if (secondsAgo >= 0 && weeksAgo < THROUGHPUT_WEEKS) {
        counts[(int) (THROUGHPUT_WEEKS - 1 - weeksAgo)]++;
      }
    }

    List<WeeklyThroughput> result = new ArrayList<>(THROUGHPUT_WEEKS);
    for (int j = 0; j < THROUGHPUT_WEEKS; j++) {
      Instant weekStart = now.minusSeconds((THROUGHPUT_WEEKS - j) * WEEK_SECONDS);
      result.add(new WeeklyThroughput(weekStart, counts[j]));
    }
    return result;
  }

  private static @Nullable Long avgLeadTime(List<Card> workCards) {
    long[] leads =
        workCards.stream()
            .map(CardCycleTimeService::leadSeconds)
            .filter(Objects::nonNull)
            .mapToLong(Long::longValue)
            .toArray();
    return average(leads);
  }

  private static @Nullable Long leadSeconds(Card card) {
    Instant done = card.movedToDoneAt();
    return done == null ? null : Duration.between(card.createdAt(), done).toSeconds();
  }

  private static @Nullable Long avgCycleTime(
      List<Card> workCards, Map<Long, List<CardColumnTransition>> byCard) {
    long[] cycles =
        workCards.stream()
            .map(card -> cycleSeconds(card, byCard.getOrDefault(card.requireId(), List.of())))
            .filter(Objects::nonNull)
            .mapToLong(Long::longValue)
            .toArray();
    return average(cycles);
  }

  private static @Nullable Long cycleSeconds(Card card, List<CardColumnTransition> cardTrans) {
    Instant done = card.movedToDoneAt();
    if (done == null) {
      return null;
    }
    Instant readyEntry =
        cardTrans.stream()
            .filter(t -> isReadyColumn(t.columnName()))
            .map(CardColumnTransition::enteredAt)
            .min(Comparator.naturalOrder())
            .orElse(null);
    return readyEntry == null ? null : Duration.between(readyEntry, done).toSeconds();
  }

  private static List<OutlierCard> outliers(
      List<Card> workCards, Map<Long, List<CardColumnTransition>> byCard, Instant now) {
    return workCards.stream()
        .flatMap(
            card ->
                byCard.getOrDefault(card.requireId(), List.of()).stream()
                    .map(t -> toOutlier(card, t, now)))
        .filter(Objects::nonNull)
        .sorted(Comparator.comparingLong(OutlierCard::dwellSeconds).reversed())
        .toList();
  }

  private static @Nullable OutlierCard toOutlier(Card card, CardColumnTransition t, Instant now) {
    Long duration = t.durationSeconds();
    long dwell = duration == null ? Duration.between(t.enteredAt(), now).toSeconds() : duration;
    if (dwell <= OUTLIER_THRESHOLD_SECONDS) {
      return null;
    }
    return new OutlierCard(card.requireId(), card.number(), card.title(), t.columnName(), dwell);
  }

  private static boolean isReadyColumn(String name) {
    return name.toLowerCase(Locale.ROOT).contains("ready");
  }

  private static @Nullable Long average(long... values) {
    if (values.length == 0) {
      return null;
    }
    long sum = 0;
    for (long value : values) {
      sum += value;
    }
    return Math.round((double) sum / values.length);
  }
}
