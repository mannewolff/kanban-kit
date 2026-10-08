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
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.BoardDashboardKpis.ColumnDwell;
import org.mwolff.manban.card.application.BoardDashboardKpis.OutlierCard;
import org.mwolff.manban.card.application.BoardDashboardKpis.WeeklyImplementation;
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
// PMD.CouplingBetweenObjects: Mit Issue #1540 kamen Wochenreihe und Zeitraum-Abruf der
// Implementierungszeit dazu — ihre Antwort-Records, der Zeitraum und die gemessene Karte zählen je
// als eigener Typ. Plan #1539 (A2) verlangt, dass Board-Gesamtwert, Wochenreihe und Zeitraum aus
// derselben Messliste filtern und die Wochen dieselben Fenster wie der Durchsatz nutzen (A4); eine
// Aufteilung auf zwei Services trennte genau diese gemeinsame Grundlage.
@SuppressWarnings("PMD.CouplingBetweenObjects")
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

    List<Card> workCards = workCards(boardId);
    List<CardColumnTransition> trans = transitions.findByBoardId(boardId);
    List<ColumnView> boardColumns = boardService.listColumns(boardId);
    Instant now = clock.instant();

    Map<Long, List<CardColumnTransition>> byCard = byCard(trans);

    // Durchschnitt und Stichprobengröße stammen aus derselben Liste — die angezeigte Datenbasis
    // kann damit nicht von der Zahl abweichen, die sie stützt.
    long[] leads = leadTimes(workCards);
    List<MeasuredCard> measured = implementationTimes(workCards, byCard);
    long[] implementations = seconds(measured);

    return new BoardDashboardKpis(
        columnDwell(boardColumns, trans),
        throughput(workCards, now),
        implementationWeekly(measured, now),
        average(leads),
        leads.length,
        average(implementations),
        implementations.length,
        outliers(workCards, byCard, now));
  }

  /**
   * Durchschnittliche Implementierungszeit der Karten eines Boards, die im Zeitraum {@code from <=
   * movedToDoneAt < to} fertig wurden (Plan #1539, E11). Ohne Grenzen gilt sie über alle gemessenen
   * Karten des Boards und gleicht damit dem Wert aus {@link #dashboard}. Erfordert dasselbe
   * Leserecht wie das Dashboard.
   *
   * @throws InvalidPeriodBoundsException nur eine Grenze gesetzt, oder {@code from} liegt nicht vor
   *     {@code to} (E12)
   */
  @Transactional(readOnly = true)
  public ImplementationTimeView implementationTime(
      long userId, long boardId, @Nullable Instant from, @Nullable Instant to) {
    permissions.requireMembership(userId, boardService.requireProjectId(boardId));
    @Nullable Period period = period(from, to);

    List<MeasuredCard> measured =
        implementationTimes(workCards(boardId), byCard(transitions.findByBoardId(boardId)));
    long[] inPeriod =
        seconds(
            measured.stream().filter(m -> period == null || period.contains(m.doneAt())).toList());
    return new ImplementationTimeView(average(inPeriod), inPeriod.length);
  }

  /** Der Zeitraum aus beiden Grenzen; {@code null} ohne Grenzen, also ohne Einschränkung. */
  private static @Nullable Period period(@Nullable Instant from, @Nullable Instant to) {
    if (from == null && to == null) {
      return null;
    }
    if (from == null || to == null) {
      throw new InvalidPeriodBoundsException(
          "Ein Zeitraum braucht beide Grenzen from und to oder keine von beiden.");
    }
    if (!from.isBefore(to)) {
      throw new InvalidPeriodBoundsException(
          "Der Beginn from muss vor dem Ende to liegen (from=" + from + ", to=" + to + ").");
    }
    return new Period(from, to);
  }

  /** Halboffener Zeitraum: {@code from} gehört dazu, {@code to} nicht (Plan #1539, E11). */
  private record Period(Instant from, Instant to) {
    boolean contains(Instant instant) {
      return !instant.isBefore(from) && instant.isBefore(to);
    }
  }

  private List<Card> workCards(long boardId) {
    return cards.findByBoardId(boardId).stream().filter(c -> c.type() == CardType.CARD).toList();
  }

  private static Map<Long, List<CardColumnTransition>> byCard(List<CardColumnTransition> trans) {
    return trans.stream().collect(Collectors.groupingBy(CardColumnTransition::cardId));
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
      if (done != null) {
        weekIndex(done, now).ifPresent(i -> counts[i]++);
      }
    }

    List<WeeklyThroughput> result = new ArrayList<>(THROUGHPUT_WEEKS);
    for (int j = 0; j < THROUGHPUT_WEEKS; j++) {
      result.add(new WeeklyThroughput(weekStart(j, now), counts[j]));
    }
    return result;
  }

  /**
   * Mittelwert der Implementierungszeit je Wochenfenster — dieselben zwölf Fenster mit demselben
   * {@code now} wie der Durchsatz (Plan #1539, A4). Eine Woche ohne gemessene Karte bleibt {@code
   * null} mit 0 Messungen.
   */
  private static List<WeeklyImplementation> implementationWeekly(
      List<MeasuredCard> measured, Instant now) {
    List<List<Long>> perWeek = new ArrayList<>(THROUGHPUT_WEEKS);
    for (int j = 0; j < THROUGHPUT_WEEKS; j++) {
      perWeek.add(new ArrayList<>());
    }
    for (MeasuredCard m : measured) {
      weekIndex(m.doneAt(), now).ifPresent(i -> perWeek.get(i).add(m.seconds()));
    }

    List<WeeklyImplementation> result = new ArrayList<>(THROUGHPUT_WEEKS);
    for (int j = 0; j < THROUGHPUT_WEEKS; j++) {
      long[] values = perWeek.get(j).stream().mapToLong(Long::longValue).toArray();
      result.add(new WeeklyImplementation(weekStart(j, now), average(values), values.length));
    }
    return result;
  }

  /**
   * Das Wochenfenster (0 = ältestes, 11 = jüngstes), in das ein Abschluss fällt; leer, wenn er in
   * der Zukunft oder zwölf Wochen und mehr zurück liegt.
   */
  private static OptionalInt weekIndex(Instant done, Instant now) {
    long secondsAgo = Duration.between(done, now).toSeconds();
    long weeksAgo = secondsAgo / WEEK_SECONDS;
    if (secondsAgo >= 0 && weeksAgo < THROUGHPUT_WEEKS) {
      return OptionalInt.of((int) (THROUGHPUT_WEEKS - 1 - weeksAgo));
    }
    return OptionalInt.empty();
  }

  private static Instant weekStart(int index, Instant now) {
    return now.minusSeconds((THROUGHPUT_WEEKS - index) * WEEK_SECONDS);
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
   * „In-Progress"-artigen Spalte lagen — je Karte ein Wert samt Abschlusszeitpunkt. Die Zählung ist
   * deshalb kleiner als die der Lead Times, sobald eine fertige Karte nie durch eine solche Spalte
   * lief. Board-Gesamtwert, Wochenreihe und Zeitraum filtern alle auf diese Liste (Plan #1539, A2).
   */
  private static List<MeasuredCard> implementationTimes(
      List<Card> workCards, Map<Long, List<CardColumnTransition>> byCard) {
    List<MeasuredCard> measured = new ArrayList<>();
    for (Card card : workCards) {
      Instant done = card.movedToDoneAt();
      if (done == null) {
        continue;
      }
      Long seconds = implementationSeconds(byCard.getOrDefault(card.requireId(), List.of()));
      if (seconds != null) {
        measured.add(new MeasuredCard(done, seconds));
      }
    }
    return measured;
  }

  private static long[] seconds(List<MeasuredCard> measured) {
    return measured.stream().mapToLong(MeasuredCard::seconds).toArray();
  }

  /** Eine gemessene Karte: wann sie fertig wurde und wie lange an ihr gearbeitet wurde. */
  private record MeasuredCard(Instant doneAt, long seconds) {}

  /**
   * Die Summe aller <em>abgeschlossenen</em> Aufenthalte einer erledigten Karte in „In-Progress"-
   * artigen Spalten; {@code null}, wenn sie keinen solchen Aufenthalt hat. Nacharbeit zählt mit:
   * Liegt eine Karte ein zweites Mal in In Progress, ist das ebenfalls Implementierungszeit. Ein
   * noch offener Aufenthalt ({@code durationSeconds == null}) ist nicht gemessen und geht nicht
   * ein.
   */
  private static @Nullable Long implementationSeconds(List<CardColumnTransition> cardTrans) {
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
    return new OutlierCard(card.requireId(), card.number(), card.title(), t.columnName(), dwell);
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
