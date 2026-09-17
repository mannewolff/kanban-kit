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
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
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
  private final BoardService boardService;
  private final PermissionChecker permissions;
  private final Clock clock;

  public CardCycleTimeService(
      CardRepository cards,
      CardColumnTransitionRepository transitions,
      BoardService boardService,
      PermissionChecker permissions,
      Clock clock) {
    this.cards = cards;
    this.transitions = transitions;
    this.boardService = boardService;
    this.permissions = permissions;
    this.clock = clock;
  }

  /**
   * Dashboard-Kennzahlen eines Boards. Erfordert Board-Leserecht (Mitglied und aufwärts, wie die
   * Board-Ansicht).
   */
  @Transactional(readOnly = true)
  public BoardDashboardKpis dashboard(long userId, long boardId) {
    permissions.requireMembership(userId, boardService.requireProjectId(boardId));

    List<Card> workCards =
        cards.findByBoardId(boardId).stream().filter(c -> c.type() == CardType.CARD).toList();
    List<CardColumnTransition> trans = transitions.findByBoardId(boardId);
    List<ColumnView> boardColumns = boardService.listColumns(boardId);
    Instant now = clock.instant();

    Map<Long, List<CardColumnTransition>> byCard =
        trans.stream().collect(Collectors.groupingBy(CardColumnTransition::cardId));

    // Durchschnitt und Stichprobengröße stammen aus derselben Liste — die angezeigte Datenbasis
    // kann damit nicht von der Zahl abweichen, die sie stützt.
    long[] leads = leadTimes(workCards);
    long[] implementations = implementationTimes(workCards, byCard);

    return new BoardDashboardKpis(
        columnDwell(boardColumns, trans),
        throughput(workCards, now),
        average(leads),
        leads.length,
        average(implementations),
        implementations.length,
        outliers(workCards, byCard, now));
  }

  private static List<ColumnDwell> columnDwell(
      List<ColumnView> boardColumns, List<CardColumnTransition> trans) {
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
              return new ColumnDwell(col.id(), col.name(), average(durations), durations.length);
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

  /** Lead Times aller abgeschlossenen Karten — je Karte ein Wert, offene Karten fallen heraus. */
  private static long[] leadTimes(List<Card> workCards) {
    return workCards.stream()
        .map(CardCycleTimeService::leadSeconds)
        .filter(Objects::nonNull)
        .mapToLong(Long::longValue)
        .toArray();
  }

  private static @Nullable Long leadSeconds(Card card) {
    Instant done = card.movedToDoneAt();
    return done == null ? null : Duration.between(card.createdAt(), done).toSeconds();
  }

  /**
   * Implementierungszeiten aller abgeschlossenen Karten, die mindestens einmal in einer
   * „In-Progress"-artigen Spalte lagen — je Karte ein Wert. Die Zählung ist deshalb kleiner als die
   * der Lead Times, sobald eine fertige Karte nie durch eine solche Spalte lief.
   */
  private static long[] implementationTimes(
      List<Card> workCards, Map<Long, List<CardColumnTransition>> byCard) {
    return workCards.stream()
        .map(card -> implementationSeconds(card, byCard.getOrDefault(card.requireId(), List.of())))
        .filter(Objects::nonNull)
        .mapToLong(Long::longValue)
        .toArray();
  }

  /**
   * Die Summe aller <em>abgeschlossenen</em> Aufenthalte einer erledigten Karte in „In-Progress"-
   * artigen Spalten; {@code null}, wenn die Karte offen ist oder keinen solchen Aufenthalt hat.
   * Nacharbeit zählt mit: Liegt eine Karte ein zweites Mal in In Progress, ist das ebenfalls
   * Implementierungszeit. Ein noch offener Aufenthalt ({@code durationSeconds == null}) ist nicht
   * gemessen und geht nicht ein.
   */
  private static @Nullable Long implementationSeconds(
      Card card, List<CardColumnTransition> cardTrans) {
    if (card.movedToDoneAt() == null) {
      return null;
    }
    long[] stays =
        cardTrans.stream()
            .filter(t -> isProgressColumn(t.columnName()))
            .map(CardColumnTransition::durationSeconds)
            .filter(Objects::nonNull)
            .mapToLong(Long::longValue)
            .toArray();
    if (stays.length == 0) {
      return null;
    }
    long sum = 0;
    for (long stay : stays) {
      sum += stay;
    }
    return sum;
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
    return new OutlierCard(
        card.requireId(), card.requireNumber(), card.title(), t.columnName(), dwell);
  }

  /**
   * Ob eine Spalte für „in Arbeit" steht — dieselbe Namensregel wie die Statusfarben des Frontends
   * ({@code lib/statusColors.ts}). Boards konfigurieren ihre Spalten selbst; eine feste Spalten-ID
   * gäbe es nicht für jedes Board.
   */
  private static boolean isProgressColumn(String name) {
    return name.toLowerCase(Locale.ROOT).contains("progress");
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
