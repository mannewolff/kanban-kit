package org.mwolff.manban.card.application;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.AgingWipView.AgingCard;
import org.mwolff.manban.card.application.CardFlowStayRepository.ErfassungsMarke;
import org.mwolff.manban.card.domain.AlterErmittlung;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.FlussErmittlung;
import org.mwolff.manban.card.domain.FlussStay;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Platten „Fluss“ und „Alter in Arbeit“ eines Boards (Plan #1765, E9, E11, E13): lädt die
 * Aufenthalte des Boards, die Erfassungsmarke und die Karten, lässt {@link FlussErmittlung} und
 * {@link AlterErmittlung} rechnen und bildet auf die Antwort ab. Recht wie das Dashboard: die
 * Mitgliedschaft im Projekt des Boards (AK 10).
 *
 * <p>Ein eigener Dienst neben {@link CardCycleTimeService}, weil jener an der PMD-Kopplungsgrenze
 * steht und nur „Fluss“ die Zone braucht (E13).
 */
@Service
public class BoardFlussService {

  private final CardFlowStayRepository aufenthalte;
  private final CardRepository cards;
  private final BoardService boardService;
  private final PermissionChecker permissions;
  private final Clock clock;

  public BoardFlussService(
      CardFlowStayRepository aufenthalte,
      CardRepository cards,
      BoardService boardService,
      PermissionChecker permissions,
      Clock clock) {
    this.aufenthalte = aufenthalte;
    this.cards = cards;
    this.boardService = boardService;
    this.permissions = permissions;
    this.clock = clock;
  }

  /**
   * Die 84 Tage der Platte „Fluss“ bis einschließlich heute in der Zone des Lesers (E9).
   *
   * @param zone Regionszone des Lesers — die Prüfung auf eine Regionszone liegt am Controller
   */
  @Transactional(readOnly = true)
  public BoardFlowView flow(long userId, long boardId, ZoneId zone) {
    permissions.requireMembership(userId, boardService.requireProjectId(boardId));
    ErfassungsMarke marke = aufenthalte.marke();
    FlussErmittlung.Fluss fluss =
        FlussErmittlung.ermittle(
            FlussErmittlung.TAGE,
            zone,
            clock.instant(),
            boardId,
            aufenthalte.findByBoardId(boardId),
            marke.reconstructedAt() != null,
            marke.since());
    return new BoardFlowView(
        fluss.tage().stream()
            .map(
                tag ->
                    new BoardFlowView.Day(
                        tag.datum(),
                        tag.known(),
                        tag.backlog(),
                        tag.ready(),
                        tag.inProgress(),
                        tag.inReview(),
                        tag.done()))
            .toList(),
        fluss.dataSince());
  }

  /** Die Zustände Ready, In Progress und In Review der Platte „Alter in Arbeit“ (E8, E10). */
  @Transactional(readOnly = true)
  public AgingWipView aging(long userId, long boardId) {
    permissions.requireMembership(userId, boardService.requireProjectId(boardId));
    List<FlussStay> verlauf = aufenthalte.findByBoardId(boardId);
    List<AlterErmittlung.Zustand> zustaende =
        AlterErmittlung.ermittle(clock.instant(), boardId, verlauf);
    Set<Long> ids =
        zustaende.stream()
            .flatMap(z -> z.cards().stream())
            .map(AlterErmittlung.Karte::cardId)
            .collect(Collectors.toSet());
    Map<Long, Card> karten =
        cards.findByIds(ids).stream()
            .collect(Collectors.toMap(c -> Objects.requireNonNull(c.id()), Function.identity()));
    return new AgingWipView(
        state(zustaende.get(0), karten),
        state(zustaende.get(1), karten),
        state(zustaende.get(2), karten));
  }

  private static AgingWipView.State state(AlterErmittlung.Zustand zustand, Map<Long, Card> karten) {
    Duration schwelle = zustand.threshold();
    return new AgingWipView.State(
        zustand.comparisonCount(),
        schwelle == null ? null : schwelle.toSeconds(),
        zustand.cards().stream()
            // Eine Karte, die zwischen beiden Abfragen endgültig gelöscht wurde, fällt heraus.
            .flatMap(k -> Stream.ofNullable(karten.get(k.cardId())).map(c -> karte(k, c)))
            .toList());
  }

  private static AgingCard karte(AlterErmittlung.Karte karte, Card card) {
    return new AgingCard(
        karte.cardId(),
        card.number(),
        card.title(),
        karte.age().toSeconds(),
        karte.ageIsMinimum(),
        karte.overThreshold());
  }
}
