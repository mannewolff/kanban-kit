package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit-Tests des Status an den Vorhaben (Plan #1294, Issue #1299): Ein Vorhaben trägt keinen
 * Status, und sein Fortschritt richtet sich nach {@code Arbeitspaket.effektivDone}. Die
 * Testmethoden stammen unverändert aus {@link CardServiceStatusTest} (Issue #1393, Plan #1387 E6).
 */
class EpicServiceStatusTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;

  private CardRepository cards;
  private BoardService boardService;
  private PermissionChecker permissions;
  private EpicService service;

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    boardService = mock(BoardService.class);
    permissions = mock(PermissionChecker.class);
    ActorContext actor = mock(ActorContext.class);
    when(actor.current()).thenReturn(ActorContext.ActorStamp.unknown());
    service =
        CardServiceAufbau.epicAusPorts(
            cards,
            mock(CardDependencyRepository.class),
            boardService,
            permissions,
            mock(CardColumnTransitionRepository.class),
            new KartenZuordnung(
                mock(CardAssigneeRepository.class),
                mock(LabelRepository.class),
                mock(CardLabelRepository.class),
                mock(PermissionChecker.class)),
            mock(CardActivityRepository.class),
            actor,
            mock(ApplicationEventPublisher.class),
            Clock.fixed(FIXED, ZoneOffset.UTC));
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(cards.save(any(Card.class))).thenAnswer(inv -> mitId(inv.getArgument(0)));
  }

  /** Die gespeicherte Karte mit Id — alle übrigen Felder, auch der Status, bleiben erhalten. */
  private static Card mitId(Card c) {
    return new Card(
        c.id() == null ? 1L : c.id(),
        c.boardId(),
        c.columnId(),
        c.number(),
        c.title(),
        c.description(),
        c.positionInColumn(),
        c.archived(),
        c.movedToDoneAt(),
        c.createdBy(),
        c.createdAt(),
        c.updatedAt(),
        c.type(),
        c.parentId(),
        c.shortcode(),
        c.dueDate(),
        c.projectId(),
        c.externalKey(),
        c.derivedFromCardId(),
        c.requirementCardId(),
        c.status());
  }

  private static ColumnView column(long id, String name, int position) {
    return new ColumnView(id, name, position, null);
  }

  private static Card vorhaben(long id, long columnId) {
    return new Card(
        id,
        BOARD,
        columnId,
        1,
        "Vorhaben",
        null,
        0,
        false,
        null,
        1L,
        FIXED,
        FIXED,
        CardType.EPIC,
        null,
        "E",
        null,
        PROJECT,
        null,
        null,
        null,
        null);
  }

  /** Karte mit Titel, Status und Done-Zeitstempel — für die Statusregeln der Schreibpfade. */
  private static Card paket(
      long id, long columnId, String title, @Nullable CardStatus status, @Nullable Instant done) {
    return new Card(
        id,
        BOARD,
        columnId,
        1,
        title,
        null,
        0,
        false,
        done,
        1L,
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

  private Card gespeichert() {
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    verify(cards).save(captor.capture());
    return captor.getValue();
  }

  @Test
  void createEpic_hatKeinenStatus() {
    when(boardService.firstColumn(BOARD)).thenReturn(column(20L, "Ready", 0));

    service.createEpic(1L, BOARD, "Vorhaben", null, null);

    assertThat(gespeichert().status()).isNull();
  }

  @Test
  void listEpics_zaehltArbeitspaketeNachIhremStatus() {
    // Pakete 6 und 8 stehen auf DONE in einer eigenen Spalte und sind erledigt; Paket 7 liegt in
    // der Done-Spalte, steht aber auf BACKLOG — für ein Arbeitspaket entscheidet der Status (E7).
    // Nach dem Spaltennamen wäre es umgekehrt: 1 von 3.
    when(boardService.listColumns(BOARD))
        .thenReturn(List.of(column(21L, "Done", 4), column(22L, "Anstehend", 5)));
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                vorhaben(5L, 21L),
                paket(6L, 22L, "Paket", CardStatus.DONE, FIXED).withParent(5L),
                paket(7L, 21L, "Paket", CardStatus.BACKLOG, null).withParent(5L),
                paket(8L, 22L, "Paket", CardStatus.DONE, FIXED).withParent(5L)));

    List<EpicService.EpicView> result = service.listEpics(1L, BOARD);

    assertThat(result)
        .singleElement()
        .extracting(EpicService.EpicView::done, EpicService.EpicView::total)
        .containsExactly(2, 3);
  }
}
