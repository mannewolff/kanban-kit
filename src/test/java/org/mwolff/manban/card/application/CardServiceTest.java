package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.board.application.ColumnNotFoundException;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.card.domain.Label;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.application.ProjectNotFoundException;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.context.ApplicationEventPublisher;

/** Verhaltenstests der Karten- und Epic-Use-Cases (Mockito an den Ports). */
// PMD.TooManyMethods: umfassende Unit-Suite (Karten + Epics, Erfolgs- und Fehlerpfade je
// Use-Case). Viele kleine @Test-Methoden sind hier gewollt, kein God-Class-Smell.
@SuppressWarnings({"PMD.TooManyMethods", "PMD.CyclomaticComplexity", "PMD.CouplingBetweenObjects"})
class CardServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;
  // Zweites Projekt/Board fuer die projektuebergreifende Nummernsuche (#489).
  private static final long PROJECT_B = 2L;
  private static final long BOARD_B = 11L;

  private CardRepository cards;
  private CardDependencyRepository dependencies;
  private BoardService boardService;
  private PermissionChecker permissions;
  private ProjectService projects;
  private CardColumnTransitionRepository transitions;
  private CardAssigneeRepository assignees;
  private LabelRepository labels;
  private CardLabelRepository cardLabels;
  private CardActivityRepository activity;
  private ActorContext actor;
  private ApplicationEventPublisher events;
  private CardService service;

  private static Card card(
      long id,
      long columnId,
      int number,
      boolean archived,
      Instant done,
      CardType type,
      Long parentId,
      String shortcode) {
    return new Card(
        id, BOARD, columnId, number, "Titel", null, 0, archived, false, done, 1L, FIXED, FIXED,
        type, parentId, shortcode, null, PROJECT, null, null, null);
  }

  private static ColumnView column(long id, String name, int position) {
    return new ColumnView(id, name, position, null);
  }

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    dependencies = mock(CardDependencyRepository.class);
    boardService = mock(BoardService.class);
    permissions = mock(PermissionChecker.class);
    projects = mock(ProjectService.class);
    transitions = mock(CardColumnTransitionRepository.class);
    assignees = mock(CardAssigneeRepository.class);
    labels = mock(LabelRepository.class);
    cardLabels = mock(CardLabelRepository.class);
    activity = mock(CardActivityRepository.class);
    actor = mock(ActorContext.class);
    when(actor.current()).thenReturn(ActorContext.ActorStamp.unknown());
    events = mock(ApplicationEventPublisher.class);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    service =
        new CardService(
            cards,
            dependencies,
            boardService,
            permissions,
            projects,
            transitions,
            assignees,
            labels,
            cardLabels,
            activity,
            actor,
            events,
            clock);
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(cards.save(any(Card.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
  }

  private static Card withId(Card c) {
    return new Card(
        c.id() == null ? 1L : c.id(),
        c.boardId(),
        c.columnId(),
        c.number(),
        c.title(),
        c.description(),
        c.positionInColumn(),
        c.archived(),
        c.ideaStored(),
        c.movedToDoneAt(),
        c.createdBy(),
        c.createdAt(),
        c.updatedAt(),
        c.type(),
        c.parentId(),
        c.shortcode(),
        c.dueDate(),
        c.projectId(),
        c.targetBoardId(),
        c.externalKey(),
        null);
  }

  // --- create -----------------------------------------------------------

  @Test
  void create_setsCreatedAtFromInjectedClock() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.allocateActivePosition(20L)).thenReturn(0);

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.create(1L, BOARD, 20L, "Titel", null, null, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().createdAt()).isEqualTo(FIXED);
  }

  @Test
  void create_setsDueDate_whenProvided() {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.allocateActivePosition(20L)).thenReturn(0);
    Instant due = Instant.parse("2026-02-01T00:00:00Z");

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    // Die zurückgegebene View der Voll-Signatur (11 Args) wird bewusst geprüft, damit der
    // @Transactional-Einstieg (der an den privaten Kern doCreate delegiert) nicht null zurückgibt.
    CardService.CardView result =
        service.create(1L, BOARD, 20L, "Titel", null, null, null, false, due, null, null);

    verify(cards).save(captor.capture());
    assertThat(captor.getValue().dueDate()).isEqualTo(due);
    assertThat(result.dueDate()).isEqualTo(due);
  }

  @Test
  void create_appliesAssignees_atomically_withSingleCreatedActivity() {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.allocateActivePosition(20L)).thenReturn(0);
    when(permissions.isRealProjectMember(7L, 1L)).thenReturn(true);
    when(permissions.isRealProjectMember(8L, 1L)).thenReturn(true);

    service.create(
        1L, BOARD, 20L, "Titel", null, null, null, false, null, List.of(7L, 8L, 7L), null);

    verify(assignees).replaceAssignees(1L, List.of(7L, 8L));
    // Genau ein Aktivitätseintrag (CREATED) — kein zusätzlicher ASSIGNED beim atomaren Anlegen.
    verify(activity)
        .add(
            1L,
            1L,
            CardActivityType.CREATED,
            "Karte angelegt",
            FIXED,
            ActorContext.ActorStamp.unknown());
  }

  @Test
  void create_stampsActivityWithActorContext() {
    // Given — der Port liefert einen Token-Stempel; die Aktivität muss ihn unverändert tragen.
    ActorContext.ActorStamp stamp =
        new ActorContext.ActorStamp(
            org.mwolff.manban.card.domain.CardActivityOrigin.TOKEN, "Nachtlauf", "claude-opus-5");
    when(actor.current()).thenReturn(stamp);
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    service.create(1L, BOARD, 20L, "Titel", null, null, null);

    // Then
    verify(activity).add(1L, 1L, CardActivityType.CREATED, "Karte angelegt", FIXED, stamp);
    verify(activity, times(1)).add(anyLong(), anyLong(), any(), any(), any(), any());
  }

  @Test
  void create_ignoresEmptyAssignees() {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.allocateActivePosition(20L)).thenReturn(0);

    service.create(1L, BOARD, 20L, "Titel", null, null, null, false, null, List.of(), null);

    verify(assignees, never()).replaceAssignees(anyLong(), anyList());
  }

  @Test
  void create_rejectsForeignAssignee() {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.allocateActivePosition(20L)).thenReturn(0);
    when(permissions.isRealProjectMember(9L, 1L)).thenReturn(false);

    assertThatThrownBy(
            () ->
                service.create(
                    1L, BOARD, 20L, "Titel", null, null, null, false, null, List.of(9L), null))
        .isInstanceOf(InvalidAssigneeException.class);
    verify(assignees, never()).replaceAssignees(anyLong(), anyList());
  }

  @Test
  void create_appliesLabels_whenProvided() {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.allocateActivePosition(20L)).thenReturn(0);
    when(labels.findByBoardId(BOARD))
        .thenReturn(
            List.of(new Label(7L, BOARD, "Bug", "#f00"), new Label(8L, BOARD, "Ux", "#0f0")));

    service.create(
        1L, BOARD, 20L, "Titel", null, null, null, false, null, null, List.of(7L, 8L, 7L));

    verify(cardLabels).replaceLabels(1L, List.of(7L, 8L));
  }

  @Test
  void create_ignoresEmptyLabels() {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.allocateActivePosition(20L)).thenReturn(0);

    service.create(1L, BOARD, 20L, "Titel", null, null, null, false, null, null, List.of());

    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  @Test
  void create_rejectsForeignLabel() {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.allocateActivePosition(20L)).thenReturn(0);
    when(labels.findByBoardId(BOARD)).thenReturn(List.of(new Label(7L, BOARD, "Bug", "#f00")));

    assertThatThrownBy(
            () ->
                service.create(
                    1L, BOARD, 20L, "Titel", null, null, null, false, null, null, List.of(8L)))
        .isInstanceOf(InvalidLabelException.class);
    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  @Test
  void create_assignsNextBoardNumber() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(8);
    when(cards.allocateActivePosition(20L)).thenReturn(0);

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.create(1L, BOARD, 20L, "Titel", null, null, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().number()).isEqualTo(8);
  }

  @Test
  void create_appendsAtNextPositionInColumn() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.allocateActivePosition(20L)).thenReturn(5);

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.create(1L, BOARD, 20L, "Titel", null, null, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().positionInColumn()).isEqualTo(5);
  }

  @Test
  void create_trimsTitle() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.create(1L, BOARD, 20L, "  Titel  ", null, null, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().title()).isEqualTo("Titel");
  }

  @Test
  void create_attachesToParentEpic() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.findById(30L))
        .thenReturn(Optional.of(card(30L, 20L, 5, false, null, CardType.EPIC, null, "E")));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.create(1L, BOARD, 20L, "Titel", null, null, 30L);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().parentId()).isEqualTo(30L);
  }

  @Test
  void create_setsDependencies_whenProvided() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(5);
    when(cards.findByProjectId(PROJECT))
        .thenReturn(List.of(card(2L, 20L, 3, false, null, CardType.CARD, null, null)));

    // When
    service.create(1L, BOARD, 20L, "Titel", null, List.of(3, 3), null);

    // Then
    verify(dependencies).replaceDependencies(1L, List.of(3));
  }

  @Test
  void create_throwsBoardNotFound_whenBoardUnknown() {
    // Given
    when(boardService.requireProjectId(BOARD)).thenThrow(new BoardNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.create(1L, BOARD, 20L, "Titel", null, null, null))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void create_throwsColumnNotFound_whenColumnUnknown() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenThrow(new ColumnNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.create(1L, BOARD, 20L, "Titel", null, null, null))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  @Test
  void create_throwsColumnNotFound_whenColumnOnOtherBoard() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenThrow(new ColumnNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.create(1L, BOARD, 20L, "Titel", null, null, null))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  @Test
  void create_throwsInvalidDependency_whenParentIsNotEpic() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.findById(30L))
        .thenReturn(Optional.of(card(30L, 20L, 5, false, null, CardType.CARD, null, null)));

    // When / Then
    assertThatThrownBy(() -> service.create(1L, BOARD, 20L, "Titel", null, null, 30L))
        .isInstanceOf(InvalidDependencyException.class);
  }

  @Test
  void create_throwsCardNotFound_whenParentUnknown() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.findById(30L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.create(1L, BOARD, 20L, "Titel", null, null, 30L))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void create_throwsInvalidDependency_onSelfDependency() {
    // Given: die eigene Nummer 1 IST eine gültige Board-Nummer. So schlägt ein Umgehen des
    // Selbstbezug-Guards (Mutant) NICHT in „Unbekannte Nummer" um, sondern in einen Erfolg —
    // der Selbstbezug-Guard wird dadurch beweisbar geprüft.
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.findByProjectId(PROJECT))
        .thenReturn(List.of(card(9L, 20L, 1, false, null, CardType.CARD, null, null)));

    // When / Then: neue Karte bekommt Nummer 1, hängt von 1 (sich selbst) ab
    List<Integer> selfDependency = List.of(1);
    assertThatThrownBy(() -> service.create(1L, BOARD, 20L, "Titel", null, selfDependency, null))
        .isInstanceOf(InvalidDependencyException.class);
  }

  @Test
  void create_throwsInvalidDependency_onUnknownDependencyNumber() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.findByProjectId(PROJECT)).thenReturn(List.of());

    // When / Then
    List<Integer> unknownDependency = List.of(99);
    assertThatThrownBy(() -> service.create(1L, BOARD, 20L, "Titel", null, unknownDependency, null))
        .isInstanceOf(InvalidDependencyException.class);
  }

  // --- createEpic -------------------------------------------------------

  @Test
  void createEpic_savesEpicType() {
    // Given
    when(boardService.firstColumn(BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.createEpic(1L, BOARD, "Epic", null, "SHC");

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().type()).isEqualTo(CardType.EPIC);
  }

  @Test
  void createEpic_trimsBlankShortcodeToNull() {
    // Given
    when(boardService.firstColumn(BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.createEpic(1L, BOARD, "Epic", null, "   ");

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().shortcode()).isNull();
  }

  @Test
  void createEpic_throwsBoardNotFound_whenBoardUnknown() {
    // Given
    when(boardService.requireProjectId(BOARD)).thenThrow(new BoardNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.createEpic(1L, BOARD, "Epic", null, null))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void createEpic_throwsColumnNotFound_whenBoardHasNoColumns() {
    // Given
    when(boardService.firstColumn(BOARD)).thenThrow(new ColumnNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.createEpic(1L, BOARD, "Epic", null, null))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  // --- listByBoard / listEpics -----------------------------------------

  @Test
  void listByBoard_returnsOnlyCards() {
    // Given
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                card(1L, 20L, 1, false, null, CardType.CARD, null, null),
                card(2L, 20L, 2, false, null, CardType.EPIC, null, "E")));

    // When
    List<CardService.CardView> result = service.listByBoard(1L, BOARD);

    // Then
    assertThat(result).singleElement().extracting(CardService.CardView::id).isEqualTo(1L);
  }

  @Test
  void listByBoard_throwsBoardNotFound_whenBoardUnknown() {
    // Given
    when(boardService.requireProjectId(BOARD)).thenThrow(new BoardNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.listByBoard(1L, BOARD))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void listEpics_countsDoneChildren() {
    // Given
    when(boardService.listColumns(BOARD))
        .thenReturn(List.of(column(20L, "Backlog", 0), column(21L, "Done", 1)));
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                card(5L, 20L, 1, false, null, CardType.EPIC, null, "E"),
                card(6L, 21L, 2, false, null, CardType.CARD, 5L, null),
                card(7L, 20L, 3, false, null, CardType.CARD, 5L, null)));

    // When
    List<CardService.EpicView> result = service.listEpics(1L, BOARD);

    // Then
    assertThat(result)
        .singleElement()
        .extracting(CardService.EpicView::done, CardService.EpicView::total)
        .containsExactly(1, 2);
  }

  @Test
  void listEpics_throwsBoardNotFound_whenBoardUnknown() {
    // Given
    when(boardService.requireProjectId(BOARD)).thenThrow(new BoardNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.listEpics(1L, BOARD))
        .isInstanceOf(BoardNotFoundException.class);
  }

  // --- update -----------------------------------------------------------

  @Test
  void update_setsCardParent() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(cards.findById(30L))
        .thenReturn(Optional.of(card(30L, 20L, 5, false, null, CardType.EPIC, null, "E")));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.update(1L, 1L, "Neu", null, null, null, 30L, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().parentId()).isEqualTo(30L);
  }

  @Test
  void update_setsEpicShortcode() {
    // Given
    when(cards.findById(5L))
        .thenReturn(Optional.of(card(5L, 20L, 5, false, null, CardType.EPIC, null, "old")));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.update(1L, 5L, "Neu", null, null, "NEW", null, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().shortcode()).isEqualTo("NEW");
  }

  @Test
  void update_replacesDependencies_whenProvided() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(cards.findByProjectId(PROJECT))
        .thenReturn(List.of(card(2L, 20L, 3, false, null, CardType.CARD, null, null)));

    // When
    service.update(1L, 1L, "Neu", null, List.of(3), null, null, null);

    // Then
    verify(dependencies).replaceDependencies(1L, List.of(3));
  }

  @Test
  void update_throwsCardNotFound_whenCardUnknown() {
    // Given
    when(cards.findById(1L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.update(1L, 1L, "Neu", null, null, null, null, null))
        .isInstanceOf(CardNotFoundException.class);
  }

  // --- assignParent -----------------------------------------------------

  @Test
  void assignParent_setsParent() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(cards.findById(30L))
        .thenReturn(Optional.of(card(30L, 20L, 5, false, null, CardType.EPIC, null, "E")));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.assignParent(1L, 1L, 30L);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().parentId()).isEqualTo(30L);
  }

  @Test
  void assignParent_throwsInvalidDependency_whenCardIsEpic() {
    // Given
    when(cards.findById(5L))
        .thenReturn(Optional.of(card(5L, 20L, 5, false, null, CardType.EPIC, null, "E")));

    // When / Then
    assertThatThrownBy(() -> service.assignParent(1L, 5L, 30L))
        .isInstanceOf(InvalidDependencyException.class);
  }

  // --- move -------------------------------------------------------------

  @Test
  void move_setsMovedToDoneAt_whenEnteringDoneColumn() {
    // Given
    Card before = card(1L, 20L, 1, false, null, CardType.CARD, null, null);
    when(cards.findById(1L)).thenReturn(Optional.of(before));
    when(boardService.requireColumn(21L, BOARD)).thenReturn(column(21L, "Done", 4));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.move(1L, 1L, 21L, 0);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().movedToDoneAt()).isEqualTo(FIXED);
  }

  @Test
  void move_clearsMovedToDoneAt_whenLeavingDoneColumn() {
    // Given
    Card before = card(1L, 21L, 1, false, FIXED.minusSeconds(10), CardType.CARD, null, null);
    when(cards.findById(1L)).thenReturn(Optional.of(before));
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.move(1L, 1L, 20L, 0);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().movedToDoneAt()).isNull();
  }

  @Test
  void move_throwsCardNotFound_whenCardUnknown() {
    // Given
    when(cards.findById(1L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.move(1L, 1L, 20L, 0))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void move_throwsInvalidDependency_forEpic() {
    // Given
    when(cards.findById(5L))
        .thenReturn(Optional.of(card(5L, 20L, 5, false, null, CardType.EPIC, null, "E")));

    // When / Then
    assertThatThrownBy(() -> service.move(1L, 5L, 20L, 0))
        .isInstanceOf(InvalidDependencyException.class);
  }

  @Test
  void move_throwsColumnNotFound_whenTargetUnknown() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(boardService.requireColumn(21L, BOARD)).thenThrow(new ColumnNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.move(1L, 1L, 21L, 0))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  @Test
  void move_throwsColumnNotFound_whenTargetOnOtherBoard() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(boardService.requireColumn(21L, BOARD)).thenThrow(new ColumnNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.move(1L, 1L, 21L, 0))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  @Test
  void move_throwsBoardNotFound_whenBoardUnknown() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(boardService.requireProjectId(BOARD)).thenThrow(new BoardNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.move(1L, 1L, 21L, 0))
        .isInstanceOf(BoardNotFoundException.class);
  }

  // --- sortColumnByNumber ------------------------------------------------

  @Test
  void sortColumnByNumber_requiresCardMove_andDelegatesAscending() {
    // Given
    when(boardService.boardIdOfColumn(20L)).thenReturn(BOARD);

    // When
    service.sortColumnByNumber(1L, 20L, SortDirection.ASC);

    // Then
    verify(permissions).require(1L, PROJECT, Permission.CARD_MOVE);
    verify(cards).sortActiveByNumber(20L, SortDirection.ASC);
  }

  @Test
  void sortColumnByNumber_delegatesDescending() {
    // Given
    when(boardService.boardIdOfColumn(20L)).thenReturn(BOARD);

    // When
    service.sortColumnByNumber(1L, 20L, SortDirection.DESC);

    // Then
    verify(cards).sortActiveByNumber(20L, SortDirection.DESC);
  }

  @Test
  void sortColumnByNumber_publishesBoardChangedEvent() {
    // Given
    when(boardService.boardIdOfColumn(20L)).thenReturn(BOARD);

    // When
    service.sortColumnByNumber(1L, 20L, SortDirection.ASC);

    // Then: offene Boards ziehen über SSE nach
    verify(events).publishEvent(new CardBoardActivityEvent(BOARD, ActivityType.MOVED, null));
  }

  @Test
  void sortColumnByNumber_throwsColumnNotFound_whenColumnUnknown() {
    // Given
    when(boardService.boardIdOfColumn(20L)).thenThrow(new ColumnNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.sortColumnByNumber(1L, 20L, SortDirection.ASC))
        .isInstanceOf(ColumnNotFoundException.class);
    verify(cards, never()).sortActiveByNumber(anyLong(), any(SortDirection.class));
    verify(events, never()).publishEvent(any());
  }

  @Test
  void sortColumnByNumber_propagatesPermissionDenied() {
    // Given
    when(boardService.boardIdOfColumn(20L)).thenReturn(BOARD);
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .require(9L, PROJECT, Permission.CARD_MOVE);

    // When / Then
    assertThatThrownBy(() -> service.sortColumnByNumber(9L, 20L, SortDirection.ASC))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(cards, never()).sortActiveByNumber(anyLong(), any(SortDirection.class));
    verify(events, never()).publishEvent(any());
  }

  // --- archive / restore / delete --------------------------------------

  @Test
  void archive_marksCardArchived() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.archive(1L, 1L);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().archived()).isTrue();
  }

  @Test
  void archive_requiresEpicDeletePermission_forEpic() {
    // Given
    when(cards.findById(5L))
        .thenReturn(Optional.of(card(5L, 20L, 5, false, null, CardType.EPIC, null, "E")));

    // When
    service.archive(1L, 5L);

    // Then
    verify(permissions).require(1L, 1L, Permission.EPIC_DELETE);
  }

  @Test
  void archive_throwsCardNotFound_whenCardUnknown() {
    // Given
    when(cards.findById(1L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.archive(1L, 1L)).isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void restore_appendsAtNextPosition() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, true, null, CardType.CARD, null, null)));
    when(cards.allocateActivePosition(20L)).thenReturn(3);

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.restore(1L, 1L);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().positionInColumn()).isEqualTo(3);
  }

  // --- Ideen-Speicher (führt seit #433 in den projektweiten Pool) -------

  @Test
  void moveToIdeaStorage_becomesBoardlessPoolIdea_keepsNumber_notesTargetBoard() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 7, false, null, CardType.CARD, null, null)));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    CardService.CardView view = service.moveToIdeaStorage(9L, 1L);

    // Then — Ideen-Pflege nutzt das Verschieberecht (kein Löschen); die Karte wird board-los,
    // behält ihre Nummer (#433, sonst brächen #N-Rückverweise) und notiert das alte Board als
    // Zielboard-Hinweis.
    verify(permissions).require(9L, 1L, Permission.CARD_MOVE);
    verify(cards).save(captor.capture());
    Card saved = captor.getValue();
    assertThat(saved.ideaStored()).isTrue();
    assertThat(saved.boardId()).isNull();
    assertThat(saved.columnId()).isNull();
    assertThat(saved.number()).isEqualTo(7);
    assertThat(saved.targetBoardId()).isEqualTo(BOARD);
    assertThat(view.ideaStored()).isTrue();
    verify(activity)
        .add(
            1L,
            9L,
            CardActivityType.IDEA_STORED,
            "In den Ideen-Speicher",
            FIXED,
            ActorContext.ActorStamp.unknown());
    verify(events).publishEvent(new ProjectIdeasChangedEvent(PROJECT));
  }

  @Test
  void moveToIdeaStorage_rejectsEpic() {
    // Given
    when(cards.findById(5L))
        .thenReturn(Optional.of(card(5L, 20L, 5, false, null, CardType.EPIC, null, "E")));

    // When / Then — Epics gehören nicht in den Ideen-Speicher; kein Save.
    assertThatThrownBy(() -> service.moveToIdeaStorage(9L, 5L))
        .isInstanceOf(InvalidDependencyException.class);
    verify(cards, never()).save(any(Card.class));
  }

  @Test
  void moveToIdeaStorage_throwsCardNotFound_whenUnknown() {
    // Given
    when(cards.findById(1L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.moveToIdeaStorage(9L, 1L))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void moveToIdeaStorage_throwsBoardNotFound_whenBoardUnknown() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(boardService.requireProjectId(BOARD)).thenThrow(new BoardNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.moveToIdeaStorage(9L, 1L))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void create_asIdea_marksIdeaStoredAndSkipsTransition() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When: direkt als Idee angelegt
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    CardService.CardView view = service.create(1L, BOARD, 20L, "Idee", null, null, null, true);

    // Then — ideaStored gesetzt, keine Spalten-Transition (kein Board-Workflow), CREATED
    // protokolliert.
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().ideaStored()).isTrue();
    assertThat(view.ideaStored()).isTrue();
    verify(transitions, never()).open(anyLong(), anyLong(), any(), any());
    verify(activity)
        .add(
            1L,
            1L,
            CardActivityType.CREATED,
            "Karte angelegt",
            FIXED,
            ActorContext.ActorStamp.unknown());
  }

  @Test
  void create_normalCard_hasIdeaStoredFalseInView() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    CardService.CardView view = service.create(1L, BOARD, 20L, "Titel", null, null, null);

    // Then — ohne Idee-Flag ist die Karte eine normale Board-Karte.
    assertThat(view.ideaStored()).isFalse();
  }

  @Test
  void delete_softDeletesCard() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    // When
    service.delete(1L, 1L);

    // Then — Löschen ist reversibel (Papierkorb), kein Hard-Delete.
    verify(cards).softDelete(1L, FIXED);
    verify(cards, never()).deleteById(anyLong());
    // Kein Epic -> keine Kinder-Entkopplung (findByBoardId bleibt ungenutzt).
    verify(cards, never()).findByBoardId(anyLong());
  }

  @Test
  void bulkDelete_softDeletesEveryCard() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(cards.findById(2L))
        .thenReturn(Optional.of(card(2L, 20L, 2, false, null, CardType.CARD, null, null)));

    // When
    service.bulkDelete(9L, List.of(1L, 2L));

    // Then
    verify(cards).softDelete(1L, FIXED);
    verify(cards).softDelete(2L, FIXED);
  }

  @Test
  void bulkDelete_propagatesAndDeletesNoneWhenOneCardUnknown() {
    // Given: erste ID unbekannt -> Fehler vor jeglichem Soft-Delete (Rollback im echten Betrieb)
    when(cards.findById(2L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.bulkDelete(9L, List.of(2L, 1L)))
        .isInstanceOf(CardNotFoundException.class);
    verify(cards, never()).softDelete(anyLong(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void delete_epicUnassignsChildrenBeforeSoftDelete() {
    when(cards.findById(5L))
        .thenReturn(Optional.of(card(5L, 20L, 5, false, null, CardType.EPIC, null, "E")));
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                card(1L, 20L, 1, false, null, CardType.CARD, 5L, null),
                card(2L, 20L, 2, false, null, CardType.CARD, null, null)));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.delete(9L, 5L);

    // Nur das Kind des Epics wird von seiner Zuordnung gelöst; danach das Epic soft-gelöscht.
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().id()).isEqualTo(1L);
    assertThat(captor.getValue().parentId()).isNull();
    verify(cards).softDelete(5L, FIXED);
  }

  @Test
  void delete_requiresTicketDeletePermission_forCard() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    // When
    service.delete(1L, 1L);

    // Then
    verify(permissions).require(1L, 1L, Permission.TICKET_DELETE);
  }

  // --- Randfälle: Zweigabdeckung ---------------------------------------

  @Test
  void listEpics_ignoresArchivedChildrenAndForeignChildren() {
    // Given: ein Epic mit einem gezählten Kind, einem archivierten Kind und einem fremden Kind
    when(boardService.listColumns(BOARD))
        .thenReturn(List.of(column(20L, "Backlog", 0), column(21L, "Done", 1)));
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                card(5L, 20L, 1, false, null, CardType.EPIC, null, "E"),
                card(6L, 20L, 2, false, null, CardType.CARD, 5L, null),
                card(7L, 20L, 3, true, null, CardType.CARD, 5L, null),
                card(8L, 20L, 4, false, null, CardType.CARD, 99L, null)));

    // When
    List<CardService.EpicView> result = service.listEpics(1L, BOARD);

    // Then: nur das nicht-archivierte, zugehörige Kind zählt
    assertThat(result).singleElement().extracting(CardService.EpicView::total).isEqualTo(1);
  }

  @Test
  void assignParent_clearsParent_whenParentNull() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, 30L, null)));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.assignParent(1L, 1L, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().parentId()).isNull();
  }

  @Test
  void create_throwsInvalidDependency_whenParentEpicOnOtherBoard() {
    // Given: Parent ist ein Epic, liegt aber auf einem anderen Board
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    Card epicOtherBoard =
        new Card(
            30L,
            99L,
            20L,
            5,
            "Epic",
            null,
            0,
            false,
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
            null);
    when(cards.findById(30L)).thenReturn(Optional.of(epicOtherBoard));

    // When / Then
    assertThatThrownBy(() -> service.create(1L, BOARD, 20L, "Titel", null, null, 30L))
        .isInstanceOf(InvalidDependencyException.class);
  }

  @Test
  void create_clearsDependencies_whenEmptyList() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    service.create(1L, BOARD, 20L, "Titel", null, List.of(), null);

    // Then
    verify(dependencies).replaceDependencies(1L, List.of());
    // Eine leere Liste wird ohne Projekt-Lookup direkt geleert (Kurzschluss des isEmpty-Zweigs).
    // Ein Umgehen dieses Zweigs (Mutant) würde die projektweiten Nummern unnötig nachladen.
    verify(cards, never()).findByProjectId(PROJECT);
  }

  @Test
  void create_clearsDependencies_whenNullList() {
    // Given: dependsOn == null muss (wie leere Liste) die Abhängigkeiten leeren. Ein Umgehen
    // des null-Zweigs (Mutant) liefe in isEmpty() auf null und würde eine NPE werfen.
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    service.create(1L, BOARD, 20L, "Titel", null, null, null);

    // Then
    verify(dependencies).replaceDependencies(1L, List.of());
  }

  @Test
  void update_leavesDependenciesUntouched_whenDependsOnNull() {
    // Given: bei dependsOn == null darf update die Abhängigkeiten NICHT anfassen. Ein Umgehen
    // des null-Guards (Mutant) würde replaceDependencies aufrufen.
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    // When
    service.update(1L, 1L, "Neu", null, null, null, null, null);

    // Then
    verify(dependencies, never()).replaceDependencies(anyLong(), anyList());
  }

  @Test
  void updateContent_keepsDescription_whenNull_andTrimsTitle() {
    // Given: der schmale Schreibweg (#571). description == null heißt „nicht ändern" — ein
    // umgedrehter Guard (Mutant) würde die vorhandene Beschreibung mit null überschreiben.
    when(cards.findById(1L))
        .thenReturn(
            Optional.of(
                card(1L, 20L, 1, false, null, CardType.CARD, null, null)
                    .withContent("Titel", "Bestand")));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    CardService.BoardItemView view = service.updateContent(1L, 1L, "  Neuer Titel  ", null);

    // Then: Beschreibung steht, Titel ist getrimmt, Rückgabe trägt den neuen Stand.
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().description()).isEqualTo("Bestand");
    assertThat(captor.getValue().title()).isEqualTo("Neuer Titel");
    assertThat(view.title()).isEqualTo("Neuer Titel");
    assertThat(view.description()).isEqualTo("Bestand");
    assertThat(view.epic()).isFalse();

    // Audit und Live-Update laufen wie beim Voll-Update — sonst wäre dieser Weg ein Schlupfloch.
    verify(activity)
        .add(
            1L,
            1L,
            CardActivityType.UPDATED,
            "Karte bearbeitet",
            FIXED,
            ActorContext.ActorStamp.unknown());
    assertThat(onlyPublishedEvent().type()).isEqualTo(ActivityType.UPDATED);
  }

  @Test
  void updateContent_clearsDescription_whenBlank() {
    // Given: ein blanker Body löscht die Beschreibung (normalize) — die Gegenprobe zum null-Fall.
    when(cards.findById(1L))
        .thenReturn(
            Optional.of(
                card(1L, 20L, 1, false, null, CardType.CARD, null, null)
                    .withContent("Titel", "Bestand")));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.updateContent(1L, 1L, "Titel", "   ");

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().description()).isNull();
  }

  @Test
  void updateContent_setsDescription_andMarksEpic() {
    // Given: ein Epic — der Typ-Zweig der Rückgabe (epic=true) und der Setz-Fall der Beschreibung.
    when(cards.findById(5L))
        .thenReturn(Optional.of(card(5L, 20L, 5, false, null, CardType.EPIC, null, "EPX")));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    CardService.BoardItemView view = service.updateContent(1L, 5L, "Neues Epic", "Neuer Rumpf");

    // Then: Inhalt gesetzt, Kürzel unangetastet (anders als beim Voll-Update).
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().description()).isEqualTo("Neuer Rumpf");
    assertThat(captor.getValue().shortcode()).isEqualTo("EPX");
    assertThat(view.epic()).isTrue();
    assertThat(view.number()).isEqualTo(5);
  }

  @Test
  void updateContent_throwsCardNotFound_whenCardUnknown() {
    // Given: keine Karte -> requireCardOp wirft, nichts wird geschrieben.
    when(cards.findById(99L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.updateContent(1L, 99L, "Neu", "Neu"))
        .isInstanceOf(CardNotFoundException.class);
    verify(cards, never()).save(any());
  }

  @Test
  void create_normalizesBlankDescriptionToNull() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.create(1L, BOARD, 20L, "Titel", "   ", null, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().description()).isNull();
  }

  @Test
  void create_keepsNonBlankDescription() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.create(1L, BOARD, 20L, "Titel", "Beschreibung", null, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().description()).isEqualTo("Beschreibung");
  }

  @Test
  void createEpic_allowsNullShortcode() {
    // Given
    when(boardService.firstColumn(BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.createEpic(1L, BOARD, "Epic", null, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().shortcode()).isNull();
  }

  @Test
  void move_keepsMovedToDoneAt_whenStayingInDoneColumn() {
    // Given: Karte ist bereits "done" und wechselt in eine andere Done-Spalte
    Instant earlier = FIXED.minusSeconds(10);
    Card before = card(1L, 104L, 1, false, earlier, CardType.CARD, null, null);
    when(cards.findById(1L)).thenReturn(Optional.of(before));
    when(boardService.requireColumn(21L, BOARD)).thenReturn(column(21L, "Done", 4));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.move(1L, 1L, 21L, 0);

    // Then: der ursprüngliche Done-Zeitpunkt bleibt erhalten
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().movedToDoneAt()).isEqualTo(earlier);
  }

  @Test
  void move_treatsNullColumnNameAsNotDone() {
    // Given: Ziel-Spalte ohne Namen -> gilt nicht als Done
    Card before = card(1L, 20L, 1, false, null, CardType.CARD, null, null);
    when(cards.findById(1L)).thenReturn(Optional.of(before));
    when(boardService.requireColumn(22L, BOARD)).thenReturn(column(22L, null, 5));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.move(1L, 1L, 22L, 0);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().movedToDoneAt()).isNull();
  }

  // --- Rückgabe-/Interaktions-Verhalten (Issue #0073, Mutationsabdeckung) ----

  @Test
  void create_returnsViewOfPersistedCard() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    CardService.CardView view = service.create(1L, BOARD, 20L, "Titel", null, null, null);

    // Then
    assertThat(view.title()).isEqualTo("Titel");
  }

  @Test
  void createEpic_assignsNextBoardNumber() {
    // Given
    when(boardService.firstColumn(BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(5);

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.createEpic(1L, BOARD, "Epic", null, "SHC");

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().number()).isEqualTo(5);
  }

  @Test
  void createEpic_returnsViewOfPersistedEpic() {
    // Given
    when(boardService.firstColumn(BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    CardService.CardView view = service.createEpic(1L, BOARD, "Epic", null, "SHC");

    // Then
    assertThat(view.title()).isEqualTo("Epic");
  }

  @Test
  void update_returnsUpdatedView() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    // When
    CardService.CardView view = service.update(1L, 1L, "Neu", null, null, null, null, null);

    // Then
    assertThat(view.title()).isEqualTo("Neu");
  }

  @Test
  void assignParent_returnsViewWithParent() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(cards.findById(30L))
        .thenReturn(Optional.of(card(30L, 20L, 5, false, null, CardType.EPIC, null, "E")));

    // When
    CardService.CardView view = service.assignParent(1L, 1L, 30L);

    // Then
    assertThat(view.parentId()).isEqualTo(30L);
  }

  @Test
  void move_persistsMoveViaRepository() {
    // Given
    Card before = card(1L, 20L, 1, false, null, CardType.CARD, null, null);
    when(cards.findById(1L)).thenReturn(Optional.of(before));
    when(boardService.requireColumn(21L, BOARD)).thenReturn(column(21L, "Done", 4));

    // When
    service.move(1L, 1L, 21L, 3);

    // Then
    verify(cards).move(1L, 21L, 3);
  }

  @Test
  void move_returnsViewOfMovedCard() {
    // Given
    Card before = card(1L, 20L, 1, false, null, CardType.CARD, null, null);
    when(cards.findById(1L)).thenReturn(Optional.of(before));
    when(boardService.requireColumn(21L, BOARD)).thenReturn(column(21L, "Done", 4));

    // When
    CardService.CardView view = service.move(1L, 1L, 21L, 0);

    // Then
    assertThat(view.id()).isEqualTo(1L);
  }

  @Test
  void archive_returnsArchivedView() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    // When
    CardService.CardView view = service.archive(1L, 1L);

    // Then
    assertThat(view.archived()).isTrue();
  }

  @Test
  void restore_returnsRestoredView() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, true, null, CardType.CARD, null, null)));

    // When
    CardService.CardView view = service.restore(1L, 1L);

    // Then
    assertThat(view.archived()).isFalse();
  }

  @Test
  void bulkArchive_archivesEveryCardAndReturnsViews() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(cards.findById(2L))
        .thenReturn(Optional.of(card(2L, 20L, 2, false, null, CardType.CARD, null, null)));

    // When
    List<CardService.CardView> result = service.bulkArchive(9L, List.of(1L, 2L));

    // Then
    assertThat(result).hasSize(2).allMatch(CardService.CardView::archived);
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    verify(cards, times(2)).save(captor.capture());
    assertThat(captor.getAllValues()).allMatch(Card::archived);
  }

  @Test
  void bulkArchive_propagatesAndStopsWhenOneCardUnknown() {
    // Given: erste ID unbekannt -> Fehler vor jeglicher Speicherung (Rollback im echten Betrieb)
    when(cards.findById(2L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.bulkArchive(9L, List.of(2L, 1L)))
        .isInstanceOf(CardNotFoundException.class);
    verify(cards, never()).save(org.mockito.ArgumentMatchers.any(Card.class));
  }

  // --- Papierkorb (Soft-Delete) -----------------------------------------

  @Test
  void restoreFromTrash_clearsDeletionAndAppends() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(cards.allocateActivePosition(20L)).thenReturn(5);

    CardService.CardView view = service.restoreFromTrash(9L, 1L);

    verify(cards).restoreFromTrash(1L, 5);
    verify(activity)
        .add(
            1L,
            9L,
            CardActivityType.RESTORED,
            "Aus Papierkorb wiederhergestellt",
            FIXED,
            ActorContext.ActorStamp.unknown());
    assertThat(view.id()).isEqualTo(1L);
  }

  @Test
  void purge_hardDeletes_forBoardManager() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.purge(9L, 1L);

    verify(permissions).require(9L, 1L, Permission.BOARD_DELETE);
    verify(dependencies).deleteByCardId(1L);
    verify(cards).deleteById(1L);
  }

  @Test
  void purge_throwsCardNotFound_whenUnknown() {
    when(cards.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.purge(9L, 1L)).isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void listTrash_returnsOnlyTrashedCards() {
    when(cards.findTrashByBoardId(BOARD))
        .thenReturn(
            List.of(
                card(1L, 20L, 1, false, null, CardType.CARD, null, null),
                card(2L, 20L, 2, false, null, CardType.EPIC, null, "E")));

    List<CardService.CardView> trash = service.listTrash(5L, BOARD);

    verify(permissions).requireMembership(5L, 1L);
    assertThat(trash).extracting(CardService.CardView::id).containsExactly(1L);
  }

  // --- transfer (board-/projektübergreifend) ----------------------------

  /**
   * Stubbt Karte, Ziel-Board (Projekt 2) und Ziel-Spalte für einen Transfer und liefert die Karte.
   */
  private void stubTransferScenario(Long parentId) {
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, FIXED, CardType.CARD, parentId, null)));
    when(boardService.requireProjectId(20L)).thenReturn(2L);
    when(boardService.requireColumn(60L, 20L)).thenReturn(new ColumnView(60L, "Backlog", 0, null));
    when(cards.allocateCardNumber(2L)).thenReturn(8);
  }

  @Test
  void transfer_movesCardToTargetBoardWithNextNumber() {
    // Given
    stubTransferScenario(9L);

    // When
    CardService.CardView view = service.transfer(1L, 100L, 20L, 60L);

    // Then
    verify(cards).transfer(100L, 20L, 60L, 8);
    assertThat(view.id()).isEqualTo(100L);
  }

  @Test
  void listBoardItems_loestDieHerkunftMitEinemEinzigenSammelzugriffAuf() {
    // Vier Karten mit drei VERSCHIEDENEN Vorfahren. Die Aufloesung darf genau einen
    // zusaetzlichen Port-Aufruf ausloesen, nicht einen je Vorfahr — sonst entstuende ein N+1
    // auf einer Liste, die ein ganzes Board umfasst.
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                card(1L, 20L, 1, false, null, CardType.CARD, null, null).withDerivedFrom(91L),
                card(2L, 20L, 2, false, null, CardType.CARD, null, null).withDerivedFrom(92L),
                card(3L, 20L, 3, false, null, CardType.CARD, null, null).withDerivedFrom(93L),
                card(4L, 20L, 4, false, null, CardType.CARD, null, null)));
    when(cards.findByIds(any()))
        .thenReturn(
            List.of(
                card(91L, 20L, 91, false, null, CardType.CARD, null, null),
                card(92L, 20L, 92, false, null, CardType.CARD, null, null),
                card(93L, 20L, 93, false, null, CardType.CARD, null, null)));

    List<CardService.BoardItemView> items = service.listBoardItems(5L, BOARD);

    verify(cards, times(1)).findByIds(any());
    verify(cards, never()).findById(anyLong());
    assertThat(items)
        .extracting(CardService.BoardItemView::derivedFrom)
        .containsExactly(91, 92, 93, null);
  }

  @Test
  void listBoardItems_fragtGarNichtNach_wennKeineKarteEineHerkunftHat() {
    // Der haeufige Fall auf einem Board ohne Herkunftsdaten: kein einziger Zugriff auf den Port.
    // Ohne diese Zusage waere die Abkuerzung wirkungslos und niemand merkte es.
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                card(1L, 20L, 1, false, null, CardType.CARD, null, null),
                card(2L, 20L, 2, false, null, CardType.CARD, null, null)));

    List<CardService.BoardItemView> items = service.listBoardItems(5L, BOARD);

    verify(cards, never()).findByIds(any());
    assertThat(items).extracting(CardService.BoardItemView::derivedFrom).containsOnlyNulls();
  }

  @Test
  void listBoardItems_liefertNull_wennDerVorfahrKeineNummerHat() {
    // Alt-Ideen von vor #402 haben number == null. Ein Verweis auf eine solche Karte liefert
    // keine Nummer — die Sicht haelt das aus, statt zu scheitern.
    Card ohneNummer =
        new Card(
            91L,
            BOARD,
            20L,
            null,
            "Alt",
            null,
            0,
            false,
            true,
            null,
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
            null);
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null).withDerivedFrom(91L)));
    when(cards.findByIds(any())).thenReturn(List.of(ohneNummer));

    List<CardService.BoardItemView> items = service.listBoardItems(5L, BOARD);

    assertThat(items).singleElement().extracting(CardService.BoardItemView::derivedFrom).isNull();
  }

  @Test
  void transfer_projektwechsel_loeschtDieHerkunftDerKarte() {
    stubTransferScenario(9L);
    when(cards.findById(100L))
        .thenReturn(
            Optional.of(
                withHerkunft(card(100L, 50L, 3, false, FIXED, CardType.CARD, 9L, null), 77L)));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.transfer(1L, 100L, 20L, 60L);

    verify(cards).save(captor.capture());
    assertThat(captor.getValue().derivedFromCardId()).isNull();
  }

  @Test
  void transfer_projektwechsel_loeschtDieHerkunftDerKinder() {
    stubTransferScenario(9L);
    Card kind = withHerkunft(card(200L, 50L, 4, false, null, CardType.CARD, null, null), 100L);
    Card archiviertesKind =
        withHerkunft(card(201L, 50L, 5, true, null, CardType.CARD, null, null), 100L);
    when(cards.findByDerivedFrom(100L)).thenReturn(List.of(kind, archiviertesKind));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.transfer(1L, 100L, 20L, 60L);

    verify(cards, times(3)).save(captor.capture());
    assertThat(captor.getAllValues())
        .filteredOn(c -> c.requireId() == 200L || c.requireId() == 201L)
        .hasSize(2)
        .allSatisfy(c -> assertThat(c.derivedFromCardId()).isNull());
  }

  @Test
  void transfer_innerhalbDesProjekts_laesstDieHerkunftUnberuehrt() {
    // Ziel-Board liegt im SELBEN Projekt: Die Kette ueberlebt den Board-Wechsel.
    when(cards.findById(100L))
        .thenReturn(
            Optional.of(
                withHerkunft(card(100L, 50L, 3, false, FIXED, CardType.CARD, 9L, null), 77L)));
    when(boardService.requireProjectId(20L)).thenReturn(PROJECT);
    when(boardService.requireColumn(60L, 20L)).thenReturn(new ColumnView(60L, "Backlog", 0, null));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.transfer(1L, 100L, 20L, 60L);

    verify(cards).save(captor.capture());
    assertThat(captor.getValue().derivedFromCardId()).isEqualTo(77L);
    verify(cards, never()).findByDerivedFrom(anyLong());
  }

  /**
   * Setzt die Herkunft auf einer Testkarte — der Record-Wither bleibt die einzige Schreibstelle.
   */
  private static Card withHerkunft(Card c, long herkunft) {
    return c.withDerivedFrom(herkunft);
  }

  @Test
  void bulkTransfer_loeschtDieHerkunftAuchWennVorfahrUndKindZusammenWandern() {
    // Wandern Vorfahr (100) und Kind (200) im selben Batch ins selbe Zielprojekt, waere die
    // Beziehung dort eigentlich weiter konsistent — sie wird trotzdem geloescht. Eine
    // Batch-Ausnahme haette das Ergebnis von der Reihenfolge innerhalb des Batches abhaengig
    // gemacht.
    stubTransferScenario(null);
    Card kind = withHerkunft(card(200L, 50L, 4, false, null, CardType.CARD, null, null), 100L);
    when(cards.findById(200L)).thenReturn(Optional.of(kind));
    when(cards.findByDerivedFrom(100L)).thenReturn(List.of(kind));
    when(cards.allocateCardNumber(2L)).thenReturn(8, 9);

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.bulkTransfer(1L, List.of(100L, 200L), 20L, 60L);

    verify(cards, times(3)).save(captor.capture());
    assertThat(captor.getAllValues())
        .filteredOn(c -> c.requireId() == 200L)
        .isNotEmpty()
        .allSatisfy(c -> assertThat(c.derivedFromCardId()).isNull());
  }

  @Test
  void transfer_clearsParentAndDependencies() {
    // Given
    stubTransferScenario(9L);

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.transfer(1L, 100L, 20L, 60L);

    // Then
    verify(dependencies).deleteByCardId(100L);
    verify(assignees).deleteByCardId(100L);
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().parentId()).isNull();
    assertThat(captor.getValue().movedToDoneAt()).isNull();
  }

  @Test
  void transfer_sameProject_keepsNumberAndKeepsDependenciesAndAssignees() {
    // Given: Ziel-Board liegt im SELBEN Projekt (1) wie die Quelle (card 100 hat projectId 1).
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, FIXED, CardType.CARD, 9L, null)));
    when(boardService.requireProjectId(20L)).thenReturn(1L);
    when(boardService.requireColumn(60L, 20L)).thenReturn(new ColumnView(60L, "Backlog", 0, null));

    // When
    service.transfer(1L, 100L, 20L, 60L);

    // Then: die Nummer (3) bleibt erhalten — keine Neuvergabe, keine Nummernvergabe …
    verify(cards).transfer(100L, 20L, 60L, 3);
    verify(cards, never()).allocateCardNumber(anyLong());
    // … und projekt-lokale Verknüpfungen wandern mit (werden NICHT gelöscht).
    verify(dependencies, never()).deleteByCardId(anyLong());
    verify(assignees, never()).deleteByCardId(anyLong());
  }

  @Test
  void transfer_acrossProjects_requiresOwnerInBothProjects() {
    // Given
    stubTransferScenario(null);

    // When
    service.transfer(1L, 100L, 20L, 60L);

    // Then — Quellprojekt (1) und Zielprojekt (2); das Verschieberecht genügt hier nicht
    verify(permissions).requireOwner(1L, 1L);
    verify(permissions).requireOwner(1L, 2L);
    verify(permissions, never()).require(anyLong(), anyLong(), any(Permission.class));
  }

  @Test
  void transfer_sameProject_requiresCardMoveInsteadOfOwner() {
    // Given: Ziel-Board liegt im SELBEN Projekt (1) wie die Quelle.
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, FIXED, CardType.CARD, null, null)));
    when(boardService.requireProjectId(20L)).thenReturn(1L);
    when(boardService.requireColumn(60L, 20L)).thenReturn(new ColumnView(60L, "Backlog", 0, null));

    // When
    service.transfer(1L, 100L, 20L, 60L);

    // Then — projektintern genügt das Verschieberecht, Eigentümer wird nicht verlangt
    verify(permissions).require(1L, 1L, Permission.CARD_MOVE);
    verify(permissions, never()).requireOwner(anyLong(), anyLong());
  }

  @Test
  void transfer_rejectsEpic() {
    // Given
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, null, CardType.EPIC, null, "EP")));

    // When / Then
    assertThatThrownBy(() -> service.transfer(1L, 100L, 20L, 60L))
        .isInstanceOf(InvalidDependencyException.class);
    verify(cards, never()).transfer(anyLong(), anyLong(), anyLong(), anyInt());
  }

  @Test
  void transfer_throwsCardNotFound_whenUnknown() {
    // Given
    when(cards.findById(100L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.transfer(1L, 100L, 20L, 60L))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void bulkTransfer_transfersEveryCardToTarget() {
    // Given: zwei Karten, gemeinsames Zielboard/-spalte
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, FIXED, CardType.CARD, null, null)));
    when(cards.findById(101L))
        .thenReturn(Optional.of(card(101L, 50L, 4, false, FIXED, CardType.CARD, null, null)));
    when(boardService.requireProjectId(20L)).thenReturn(2L);
    when(boardService.requireColumn(60L, 20L)).thenReturn(new ColumnView(60L, "Backlog", 0, null));
    when(cards.allocateCardNumber(2L)).thenReturn(8);

    // When
    List<CardService.CardView> result = service.bulkTransfer(1L, List.of(100L, 101L), 20L, 60L);

    // Then — die Views je Karte werden zurückgegeben (nicht null)
    assertThat(result).extracting(CardService.CardView::id).containsExactly(100L, 101L);
    verify(cards).transfer(100L, 20L, 60L, 8);
    verify(cards).transfer(101L, 20L, 60L, 8);
  }

  @Test
  void bulkTransfer_locksTargetAndAllSourceColumnsInOneGo() {
    // Given: zwei Karten aus verschiedenen Quellspalten
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, FIXED, CardType.CARD, null, null)));
    when(cards.findById(101L))
        .thenReturn(Optional.of(card(101L, 51L, 4, false, FIXED, CardType.CARD, null, null)));
    when(boardService.requireProjectId(20L)).thenReturn(2L);
    when(boardService.requireColumn(60L, 20L)).thenReturn(new ColumnView(60L, "Backlog", 0, null));
    when(cards.allocateCardNumber(2L)).thenReturn(8);

    // When
    service.bulkTransfer(1L, List.of(100L, 101L), 20L, 60L);

    // Then: Zielspalte und beide Quellspalten in einem einzigen Sperraufruf — nähme jeder
    // Einzel-Umzug seine Sperren für sich, könnten zwei Sammel-Umzüge sie über Kreuz greifen
    // und verklemmen (#499). Das Zielprojekt (2) ist ein anderes als das der Karten (1), also
    // werden Nummern neu vergeben — die Projektsperre muss vor der Spaltensperre liegen.
    InOrder inOrder = inOrder(cards);
    inOrder.verify(cards).lockCardNumbers(2L);
    inOrder.verify(cards).lockColumnPositions(List.of(60L, 50L, 51L));
  }

  @Test
  void bulkTransfer_doesNotLockTheNumberSpace_withinTheSameProject() {
    // Given: Ziel- und Quellprojekt sind identisch — es wird keine Nummer neu vergeben
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, FIXED, CardType.CARD, null, null)));
    when(boardService.requireProjectId(20L)).thenReturn(PROJECT);
    when(boardService.requireColumn(60L, 20L)).thenReturn(new ColumnView(60L, "Backlog", 0, null));

    // When
    service.bulkTransfer(1L, List.of(100L), 20L, 60L);

    // Then: ein Sammel-Umzug im eigenen Projekt bremst die Karten-Anlage dort nicht aus.
    verify(cards, never()).lockCardNumbers(anyLong());
    verify(cards).lockColumnPositions(List.of(60L, 50L));
  }

  @Test
  void bulkTransfer_propagatesAndTransfersNoneWhenOneIsEpic() {
    // Given: erste Karte ein Epic -> Abbruch vor jeglichem Transfer (Rollback im echten Betrieb)
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, null, CardType.EPIC, null, "EP")));

    // When / Then
    assertThatThrownBy(() -> service.bulkTransfer(1L, List.of(100L, 101L), 20L, 60L))
        .isInstanceOf(InvalidDependencyException.class);
    verify(cards, never()).transfer(anyLong(), anyLong(), anyLong(), anyInt());
  }

  @Test
  void transfer_throwsBoardNotFound_whenTargetBoardUnknown() {
    // Given
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, null, CardType.CARD, null, null)));
    when(boardService.requireProjectId(20L)).thenThrow(new BoardNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.transfer(1L, 100L, 20L, 60L))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void transfer_throwsColumnNotFound_whenTargetColumnInOtherBoard() {
    // Given
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, null, CardType.CARD, null, null)));
    when(boardService.requireProjectId(20L)).thenReturn(2L);
    when(boardService.requireColumn(60L, 20L)).thenThrow(new ColumnNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.transfer(1L, 100L, 20L, 60L))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  @Test
  void update_setsDueDate_forCard() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    Instant due = FIXED.plusSeconds(86_400);

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    CardService.CardView view = service.update(1L, 1L, "Neu", null, null, null, null, due);

    verify(cards).save(captor.capture());
    assertThat(captor.getValue().dueDate()).isEqualTo(due);
    assertThat(view.dueDate()).isEqualTo(due);
  }

  // --- Zuständige (Assignees) -------------------------------------------

  @Test
  void setAssignees_replacesWithDistinctMembers() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(permissions.isRealProjectMember(7L, 1L)).thenReturn(true);
    when(permissions.isRealProjectMember(8L, 1L)).thenReturn(true);
    when(assignees.findByCardId(1L)).thenReturn(List.of(7L, 8L));

    CardService.CardView result = service.setAssignees(3L, 1L, List.of(7L, 8L, 7L));

    verify(permissions).require(3L, 1L, Permission.TICKET_UPDATE);
    verify(assignees).replaceAssignees(1L, List.of(7L, 8L));
    verify(activity)
        .add(
            1L,
            3L,
            CardActivityType.ASSIGNED,
            "Zuständige geändert",
            FIXED,
            ActorContext.ActorStamp.unknown());
    assertThat(result.id()).isEqualTo(1L);
    assertThat(result.assignees()).containsExactly(7L, 8L);
  }

  @Test
  void setAssignees_rejectsNonMember() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(permissions.isRealProjectMember(7L, 1L)).thenReturn(true);
    when(permissions.isRealProjectMember(8L, 1L)).thenReturn(false);

    assertThatThrownBy(() -> service.setAssignees(3L, 1L, List.of(7L, 8L)))
        .isInstanceOf(InvalidAssigneeException.class);
    verify(assignees, never()).replaceAssignees(anyLong(), anyList());
  }

  @Test
  void setAssignees_rejectsEpic() {
    when(cards.findById(5L))
        .thenReturn(Optional.of(card(5L, 20L, 5, false, null, CardType.EPIC, null, "E")));

    assertThatThrownBy(() -> service.setAssignees(3L, 5L, List.of(7L)))
        .isInstanceOf(InvalidDependencyException.class);
    verify(assignees, never()).replaceAssignees(anyLong(), anyList());
  }

  @Test
  void setAssignees_throwsCardNotFound_whenUnknown() {
    when(cards.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.setAssignees(3L, 1L, List.of()))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void setAssignees_propagatesPermissionDenied() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .require(9L, 1L, Permission.TICKET_UPDATE);

    assertThatThrownBy(() -> service.setAssignees(9L, 1L, List.of()))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(assignees, never()).replaceAssignees(anyLong(), anyList());
  }

  // --- Labels -----------------------------------------------------------

  @Test
  void setLabels_replacesWithDistinctBoardLabels() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(labels.findByBoardId(BOARD))
        .thenReturn(
            List.of(new Label(7L, BOARD, "Bug", "#f00"), new Label(8L, BOARD, "Ux", "#0f0")));
    when(cardLabels.findByCardId(1L)).thenReturn(List.of(7L, 8L));

    CardService.CardView view = service.setLabels(3L, 1L, List.of(7L, 8L, 7L));

    verify(permissions).require(3L, 1L, Permission.TICKET_UPDATE);
    verify(cardLabels).replaceLabels(1L, List.of(7L, 8L));
    assertThat(view.labels()).containsExactly(7L, 8L);
  }

  @Test
  void setLabels_rejectsForeignLabel() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(labels.findByBoardId(BOARD)).thenReturn(List.of(new Label(7L, BOARD, "Bug", "#f00")));

    assertThatThrownBy(() -> service.setLabels(3L, 1L, List.of(7L, 8L)))
        .isInstanceOf(InvalidLabelException.class);
    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  @Test
  void setLabels_rejectsEpic() {
    when(cards.findById(5L))
        .thenReturn(Optional.of(card(5L, 20L, 5, false, null, CardType.EPIC, null, "E")));

    assertThatThrownBy(() -> service.setLabels(3L, 5L, List.of(7L)))
        .isInstanceOf(InvalidDependencyException.class);
    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  @Test
  void setLabels_throwsCardNotFound_whenUnknown() {
    when(cards.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.setLabels(3L, 1L, List.of()))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void setLabels_propagatesPermissionDenied() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .require(9L, 1L, Permission.TICKET_UPDATE);

    assertThatThrownBy(() -> service.setLabels(9L, 1L, List.of()))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  // --- Aktivitätsverlauf (card_activity) --------------------------------

  @Test
  void create_recordsCreatedActivity() {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    service.create(1L, BOARD, 20L, "Titel", null, null, null);

    verify(activity)
        .add(
            1L,
            1L,
            CardActivityType.CREATED,
            "Karte angelegt",
            FIXED,
            ActorContext.ActorStamp.unknown());
  }

  @Test
  void move_recordsMovedActivity_whenColumnChanges() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(boardService.requireColumn(21L, BOARD)).thenReturn(column(21L, "Done", 4));

    service.move(9L, 1L, 21L, 0);

    verify(activity)
        .add(
            1L,
            9L,
            CardActivityType.MOVED,
            "Verschoben nach Done",
            FIXED,
            ActorContext.ActorStamp.unknown());
  }

  @Test
  void update_recordsUpdatedActivity() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.update(9L, 1L, "Neu", null, null, null, null, null);

    verify(activity)
        .add(
            1L,
            9L,
            CardActivityType.UPDATED,
            "Karte bearbeitet",
            FIXED,
            ActorContext.ActorStamp.unknown());
  }

  @Test
  void archive_recordsArchivedActivity() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.archive(9L, 1L);

    verify(activity)
        .add(
            1L,
            9L,
            CardActivityType.ARCHIVED,
            "Archiviert",
            FIXED,
            ActorContext.ActorStamp.unknown());
  }

  @Test
  void restore_recordsRestoredActivity() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, true, null, CardType.CARD, null, null)));

    service.restore(9L, 1L);

    verify(activity)
        .add(
            1L,
            9L,
            CardActivityType.RESTORED,
            "Wiederhergestellt",
            FIXED,
            ActorContext.ActorStamp.unknown());
  }

  @Test
  void listActivity_returnsHistoryForMember() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    CardActivity entry =
        new CardActivity(
            3L, 1L, 9L, CardActivityType.CREATED, "Karte angelegt", FIXED, null, null, null);
    when(activity.findByCardId(1L)).thenReturn(List.of(entry));

    List<CardActivity> result = service.listActivity(5L, 1L);

    verify(permissions).requireMembership(5L, 1L);
    assertThat(result).containsExactly(entry);
  }

  @Test
  void listActivity_throwsCardNotFound_whenUnknown() {
    when(cards.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.listActivity(5L, 1L))
        .isInstanceOf(CardNotFoundException.class);
  }

  // --- Zykluszeit-Tracking (card_column_transition) ---------------------

  @Test
  void create_opensColumnTransition() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.allocateActivePosition(20L)).thenReturn(0);

    // When
    service.create(1L, BOARD, 20L, "Titel", null, null, null);

    // Then — Eintritt in die Zielspalte wird mit dem Erstellzeitpunkt eröffnet.
    verify(transitions).open(1L, 20L, "Backlog", FIXED);
  }

  @Test
  void move_closesOldAndOpensNewTransition_whenColumnChanges() {
    // Given
    Card before = card(1L, 20L, 1, false, null, CardType.CARD, null, null);
    when(cards.findById(1L)).thenReturn(Optional.of(before));
    when(boardService.requireColumn(21L, BOARD)).thenReturn(column(21L, "Done", 4));

    // When
    service.move(1L, 1L, 21L, 0);

    // Then — erst die verlassene Spalte schließen, dann die Zielspalte eröffnen.
    InOrder order = inOrder(transitions);
    order.verify(transitions).closeOpen(1L, FIXED);
    order.verify(transitions).open(1L, 21L, "Done", FIXED);
  }

  @Test
  void move_recordsNoTransition_whenColumnUnchanged() {
    // Given: Reindex innerhalb derselben Spalte (Ziel == aktuelle Spalte).
    Card before = card(1L, 20L, 1, false, null, CardType.CARD, null, null);
    when(cards.findById(1L)).thenReturn(Optional.of(before));
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    service.move(1L, 1L, 20L, 2);

    // Then — kein Spaltenwechsel, keine Transition.
    verify(transitions, never()).closeOpen(anyLong(), any());
    verify(transitions, never()).open(anyLong(), anyLong(), any(), any());
  }

  @Test
  void transfer_recordsColumnTransition() {
    // Given
    stubTransferScenario(null);

    // When
    service.transfer(1L, 100L, 20L, 60L);

    // Then — Umzug schließt die alte und eröffnet die Ziel-Spalte.
    InOrder order = inOrder(transitions);
    order.verify(transitions).closeOpen(100L, FIXED);
    order.verify(transitions).open(100L, 60L, "Backlog", FIXED);
  }

  // --- Live-Board-Events (#342): je board-relevanter Mutation ein BoardChangedEvent ------------

  private CardBoardActivityEvent onlyPublishedEvent() {
    ArgumentCaptor<CardBoardActivityEvent> captor =
        ArgumentCaptor.forClass(CardBoardActivityEvent.class);
    verify(events).publishEvent(captor.capture());
    return captor.getValue();
  }

  @Test
  void create_publishesCreatedEvent() {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    service.create(1L, BOARD, 20L, "Titel", null, null, null);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.CREATED, 1L));
  }

  @Test
  void createEpic_publishesCreatedEvent() {
    when(boardService.firstColumn(BOARD)).thenReturn(column(20L, "Backlog", 0));

    service.createEpic(1L, BOARD, "Epic", null, "E");

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.CREATED, 1L));
  }

  @Test
  void update_publishesUpdatedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.update(1L, 1L, "Neu", null, null, null, null, null);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.UPDATED, 1L));
  }

  @Test
  void setAssignees_publishesUpdatedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.setAssignees(3L, 1L, List.of());

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.UPDATED, 1L));
  }

  @Test
  void setLabels_publishesUpdatedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.setLabels(3L, 1L, List.of());

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.UPDATED, 1L));
  }

  @Test
  void assignParent_publishesUpdatedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.assignParent(1L, 1L, null);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.UPDATED, 1L));
  }

  @Test
  void move_publishesMovedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(boardService.requireColumn(21L, BOARD)).thenReturn(column(21L, "Ready", 1));

    service.move(1L, 1L, 21L, 0);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.MOVED, 1L));
  }

  @Test
  void archive_publishesArchivedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.archive(1L, 1L);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.ARCHIVED, 1L));
  }

  @Test
  void restore_publishesRestoredEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, true, null, CardType.CARD, null, null)));

    service.restore(1L, 1L);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.RESTORED, 1L));
  }

  @Test
  void moveToIdeaStorage_publishesMovedAndIdeasChangedEvent() {
    // Seit #433 publiziert die Methode zwei Events (das Board muss die Karte verschwinden lassen,
    // der Pool sie zeigen) — anders als bei einem Einzel-Event genügt hier je ein gezielter
    // verify() statt der onlyPublishedEvent()-Kurzform.
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.moveToIdeaStorage(9L, 1L);

    verify(events).publishEvent(new CardBoardActivityEvent(BOARD, ActivityType.MOVED, 1L));
    verify(events).publishEvent(new ProjectIdeasChangedEvent(PROJECT));
  }

  @Test
  void delete_publishesDeletedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.delete(1L, 1L);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.DELETED, 1L));
  }

  @Test
  void restoreFromTrash_publishesRestoredEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.restoreFromTrash(9L, 1L);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.RESTORED, 1L));
  }

  @Test
  void purge_publishesDeletedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.purge(9L, 1L);

    // Seit #503 publiziert der Purge zusätzlich CardsPurgedEvent (Anhang-Aufräumkette).
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(events, times(2)).publishEvent(captor.capture());
    assertThat(captor.getAllValues())
        .containsExactly(
            new CardsPurgedEvent(List.of(1L)),
            new CardBoardActivityEvent(BOARD, ActivityType.DELETED, 1L));
  }

  @Test
  void purge_publishesCardsPurgedBeforeDeleting() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.purge(9L, 1L);

    // Reihenfolge ist die Zusage aus #503: Erst publizieren (Anhänge planen ihre Blob-Löschung
    // ein, solange die Metadaten existieren), dann löschen — die Cascade nimmt die Metadaten mit.
    InOrder inOrder = inOrder(events, cards);
    inOrder.verify(events).publishEvent(new CardsPurgedEvent(List.of(1L)));
    inOrder.verify(cards).deleteById(1L);
  }

  @Test
  void transfer_publishesMovedEventForBothBoards() {
    stubTransferScenario(null);

    service.transfer(1L, 100L, 20L, 60L);

    ArgumentCaptor<CardBoardActivityEvent> captor =
        ArgumentCaptor.forClass(CardBoardActivityEvent.class);
    verify(events, times(2)).publishEvent(captor.capture());
    assertThat(captor.getAllValues())
        .containsExactly(
            new CardBoardActivityEvent(BOARD, ActivityType.MOVED, 100L),
            new CardBoardActivityEvent(20L, ActivityType.MOVED, 100L));
  }

  @Test
  void failedMutation_publishesNoEvent() {
    when(cards.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.archive(1L, 1L)).isInstanceOf(CardNotFoundException.class);

    verify(events, never()).publishEvent(any());
  }

  // --- Projektweiter Ideen-Pool (#372) -----------------------------------

  private static Card poolIdea(long id) {
    return new Card(
        id,
        null,
        null,
        null,
        "Idee",
        null,
        0,
        false,
        true,
        null,
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
        null);
  }

  @Test
  void createProjectIdea_savesBoardlessIdea_withProjectAndTargetBoard() {
    // Neue Pool-Ideen bekommen sofort eine projektweite Nummer (#402), bleiben aber board-los.
    when(cards.allocateCardNumber(PROJECT)).thenReturn(3);
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    CardService.CardView view = service.createProjectIdea(1L, PROJECT, "Idee", "d", 7L);

    verify(permissions).require(1L, PROJECT, Permission.TICKET_CREATE);
    verify(cards).allocateCardNumber(PROJECT);
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().boardId()).isNull();
    assertThat(captor.getValue().columnId()).isNull();
    assertThat(captor.getValue().number()).isEqualTo(3);
    assertThat(captor.getValue().ideaStored()).isTrue();
    assertThat(captor.getValue().projectId()).isEqualTo(PROJECT);
    assertThat(captor.getValue().targetBoardId()).isEqualTo(7L);
    verify(activity)
        .add(
            1L,
            1L,
            CardActivityType.CREATED,
            "Idee angelegt",
            FIXED,
            ActorContext.ActorStamp.unknown());
    assertThat(view.boardId()).isNull();
    assertThat(view.number()).isEqualTo(3);
    // view() muss das notierte Zielboard durchreichen — das Frontend wählt es beim Einplanen vor.
    assertThat(view.targetBoardId()).isEqualTo(7L);
  }

  @Test
  void planOntoBoard_movesIdeaIntoBacklog_assignsNumberPosition_andPublishes() {
    when(cards.findById(1L)).thenReturn(Optional.of(poolIdea(1L)));
    when(boardService.firstColumn(BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(5);
    when(cards.allocateActivePosition(20L)).thenReturn(3);

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    CardService.CardView result = service.planOntoBoard(9L, 1L, BOARD);

    verify(permissions).require(9L, PROJECT, Permission.TICKET_CREATE);
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().boardId()).isEqualTo(BOARD);
    assertThat(captor.getValue().columnId()).isEqualTo(20L);
    assertThat(captor.getValue().number()).isEqualTo(5);
    assertThat(captor.getValue().positionInColumn()).isEqualTo(3);
    assertThat(captor.getValue().ideaStored()).isFalse();
    verify(transitions).open(1L, 20L, "Backlog", FIXED);
    verify(activity)
        .add(
            1L,
            9L,
            CardActivityType.PROMOTED,
            "Auf Board eingeplant",
            FIXED,
            ActorContext.ActorStamp.unknown());
    verify(events).publishEvent(new CardBoardActivityEvent(BOARD, ActivityType.CREATED, 1L));
    assertThat(result.boardId()).isEqualTo(BOARD);
    assertThat(result.ideaStored()).isFalse();
  }

  @Test
  void planOntoBoard_keepsExistingNumber_forAlreadyNumberedIdea() {
    // Seit #402 tragen Pool-Ideen bereits bei der Anlage eine projektweite Nummer; beim Einplanen
    // wird sie behalten (keine Neuvergabe).
    Card numbered =
        new Card(
            1L,
            null,
            null,
            42,
            "Idee",
            null,
            0,
            false,
            true,
            null,
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
            null);
    when(cards.findById(1L)).thenReturn(Optional.of(numbered));
    when(boardService.firstColumn(BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateActivePosition(20L)).thenReturn(0);

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.planOntoBoard(9L, 1L, BOARD);

    verify(cards).save(captor.capture());
    assertThat(captor.getValue().number()).isEqualTo(42);
    verify(cards, never()).allocateCardNumber(anyLong());
  }

  @Test
  void planOntoBoard_rejectsBoardOfOtherProject() {
    when(cards.findById(1L)).thenReturn(Optional.of(poolIdea(1L)));
    when(boardService.requireProjectId(BOARD)).thenReturn(99L);

    assertThatThrownBy(() -> service.planOntoBoard(9L, 1L, BOARD))
        .isInstanceOf(BoardNotFoundException.class);
    verify(cards, never()).save(any(Card.class));
  }

  @Test
  void moveBackToPool_makesCardBoardless_notesOldBoard_andPublishes() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    CardService.CardView result = service.moveBackToPool(9L, 1L);

    verify(permissions).require(9L, PROJECT, Permission.CARD_MOVE);
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().boardId()).isNull();
    assertThat(captor.getValue().ideaStored()).isTrue();
    assertThat(captor.getValue().targetBoardId()).isEqualTo(BOARD);
    verify(activity)
        .add(
            1L,
            9L,
            CardActivityType.IDEA_STORED,
            "Zurück in den Ideen-Pool",
            FIXED,
            ActorContext.ActorStamp.unknown());
    verify(events).publishEvent(new CardBoardActivityEvent(BOARD, ActivityType.MOVED, 1L));
    assertThat(result.boardId()).isNull();
  }

  @Test
  void createProjectIdea_publishesIdeasChanged() {
    service.createProjectIdea(1L, PROJECT, "Idee", "d", 7L);

    verify(events).publishEvent(new ProjectIdeasChangedEvent(PROJECT));
  }

  @Test
  void createProjectIdeas_createsEveryIdea_withOwnNumberAndSharedTargetBoard() {
    when(cards.allocateCardNumber(PROJECT)).thenReturn(3, 4);
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);

    List<CardService.CardView> views =
        service.createProjectIdeas(
            1L,
            PROJECT,
            List.of(new CardService.NewIdea("Erste", "a"), new CardService.NewIdea("Zweite", null)),
            7L);

    verify(cards, times(2)).save(captor.capture());
    assertThat(captor.getAllValues()).extracting(Card::title).containsExactly("Erste", "Zweite");
    assertThat(captor.getAllValues()).extracting(Card::description).containsExactly("a", null);
    assertThat(captor.getAllValues()).extracting(Card::number).containsExactly(3, 4);
    assertThat(captor.getAllValues())
        .allMatch(c -> c.ideaStored() && c.boardId() == null && c.targetBoardId() == 7L);
    // Beide Speicherungen liefern im Mock dieselbe Id (1L) — je Idee entsteht ein CREATED-Eintrag.
    verify(activity, times(2))
        .add(
            1L,
            1L,
            CardActivityType.CREATED,
            "Idee angelegt",
            FIXED,
            ActorContext.ActorStamp.unknown());
    assertThat(views).hasSize(2).extracting(CardService.CardView::number).containsExactly(3, 4);
  }

  @Test
  void createProjectIdeas_checksTicketCreateOnce_andPublishesOneIdeasChangedEvent() {
    // Ein Ereignis fuer den ganzen Stapel genuegt: der Ideen-Pool laedt danach ohnehin komplett
    // neu.
    service.createProjectIdeas(
        1L,
        PROJECT,
        List.of(
            new CardService.NewIdea("Erste", null),
            new CardService.NewIdea("Zweite", null),
            new CardService.NewIdea("Dritte", null)),
        null);

    verify(permissions, times(1)).require(1L, PROJECT, Permission.TICKET_CREATE);
    verify(events, times(1)).publishEvent(new ProjectIdeasChangedEvent(PROJECT));
  }

  @Test
  void createProjectIdeas_withoutTicketCreate_createsNothing() {
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .require(1L, PROJECT, Permission.TICKET_CREATE);

    assertThatThrownBy(
            () ->
                service.createProjectIdeas(
                    1L, PROJECT, List.of(new CardService.NewIdea("Erste", null)), null))
        .isInstanceOf(ProjectAccessDeniedException.class);

    verify(cards, never()).save(any(Card.class));
    verify(events, never()).publishEvent(any(ProjectIdeasChangedEvent.class));
  }

  @Test
  void planOntoBoard_publishesIdeasChanged() {
    when(cards.findById(1L)).thenReturn(Optional.of(poolIdea(1L)));
    when(boardService.firstColumn(BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(5);
    when(cards.allocateActivePosition(20L)).thenReturn(3);

    service.planOntoBoard(9L, 1L, BOARD);

    verify(events).publishEvent(new ProjectIdeasChangedEvent(PROJECT));
  }

  @Test
  void moveBackToPool_publishesIdeasChanged() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.moveBackToPool(9L, 1L);

    verify(events).publishEvent(new ProjectIdeasChangedEvent(PROJECT));
  }

  @Test
  void listProjectIdeas_returnsOnlyCards_forMember() {
    when(cards.findIdeasByProjectId(PROJECT))
        .thenReturn(List.of(poolIdea(1L), card(2L, 20L, 2, false, null, CardType.EPIC, null, "E")));

    List<CardService.CardView> result = service.listProjectIdeas(1L, PROJECT);

    verify(permissions).requireMembership(1L, PROJECT);
    assertThat(result).singleElement().extracting(CardService.CardView::id).isEqualTo(1L);
  }

  // --- getByNumber (#408) -----------------------------------------------

  @Test
  void getByNumber_returnsBoardCardView_forMember() {
    when(cards.findByProjectIdAndNumber(PROJECT, 42))
        .thenReturn(Optional.of(card(1L, 20L, 42, false, null, CardType.CARD, null, null)));

    CardService.CardView view = service.getByNumber(5L, PROJECT, 42);

    assertThat(view.id()).isEqualTo(1L);
    assertThat(view.number()).isEqualTo(42);
    assertThat(view.boardId()).isEqualTo(BOARD);
  }

  @Test
  void getByNumber_returnsPoolIdeaView_forMember() {
    // Auch eine board-lose Pool-Idee ist per projektweiter Nummer auflösbar.
    when(cards.findByProjectIdAndNumber(PROJECT, 7)).thenReturn(Optional.of(poolIdea(1L)));

    CardService.CardView view = service.getByNumber(5L, PROJECT, 7);

    assertThat(view.id()).isEqualTo(1L);
    assertThat(view.boardId()).isNull();
    assertThat(view.ideaStored()).isTrue();
  }

  @Test
  void getByNumber_checksMembershipBeforeLookup() {
    // Reihenfolge: erst Mitgliedschaft (404 bei Nichtmitglied), dann Karten-Lookup.
    when(cards.findByProjectIdAndNumber(PROJECT, 42))
        .thenReturn(Optional.of(card(1L, 20L, 42, false, null, CardType.CARD, null, null)));

    service.getByNumber(5L, PROJECT, 42);

    InOrder order = inOrder(permissions, cards);
    order.verify(permissions).requireMembership(5L, PROJECT);
    order.verify(cards).findByProjectIdAndNumber(PROJECT, 42);
  }

  @Test
  void getByNumber_propagatesMembership404_forNonMember() {
    doThrow(new ProjectNotFoundException()).when(permissions).requireMembership(5L, PROJECT);

    assertThatThrownBy(() -> service.getByNumber(5L, PROJECT, 42))
        .isInstanceOf(ProjectNotFoundException.class);
    verify(cards, never()).findByProjectIdAndNumber(anyLong(), anyInt());
  }

  @Test
  void getByNumber_throwsCardNotFound_whenUnknownNumber() {
    when(cards.findByProjectIdAndNumber(PROJECT, 99)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getByNumber(5L, PROJECT, 99))
        .isInstanceOf(CardNotFoundException.class);
  }

  // --- searchByNumber: projektuebergreifende Nummernsuche (#489) --------

  private static ProjectService.AccessibleProject accessible(long id, String name) {
    return new ProjectService.AccessibleProject(id, name);
  }

  private static Card cardIn(long id, long projectId, long boardId, long columnId, int number) {
    return new Card(
        id,
        boardId,
        columnId,
        number,
        "Titel",
        null,
        0,
        false,
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
        null,
        null);
  }

  @Test
  void searchByNumber_returnsHitWithProjectBoardAndColumn() {
    when(projects.listAccessible(5L)).thenReturn(List.of(accessible(PROJECT, "Projekt A")));
    when(cards.findByNumberInProjects(42, List.of(PROJECT)))
        .thenReturn(List.of(cardIn(1L, PROJECT, BOARD, 20L, 42)));
    when(boardService.requireBoardSummary(BOARD))
        .thenReturn(new BoardService.BoardSummary(BOARD, "Board A", false));
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Ready", 1));

    List<CardService.CardSearchHit> hits = service.searchByNumber(5L, 42);

    assertThat(hits)
        .singleElement()
        .isEqualTo(
            new CardService.CardSearchHit(
                hits.get(0).card(), PROJECT, "Projekt A", BOARD, "Board A", false, 20L, "Ready"));
    assertThat(hits.get(0).card().id()).isEqualTo(1L);
  }

  @Test
  void searchByNumber_returnsBothHits_whenNumberExistsInTwoOwnProjects() {
    // Kartennummern sind projektweit eindeutig, nicht global: derselbe Wert kann in mehreren
    // Projekten liegen, und dann sind alle Treffer gemeint (unterscheidbar am Projektnamen).
    when(projects.listAccessible(5L))
        .thenReturn(List.of(accessible(PROJECT, "Projekt A"), accessible(PROJECT_B, "Projekt B")));
    when(cards.findByNumberInProjects(42, List.of(PROJECT, PROJECT_B)))
        .thenReturn(
            List.of(cardIn(1L, PROJECT, BOARD, 20L, 42), cardIn(2L, PROJECT_B, BOARD_B, 30L, 42)));
    when(boardService.requireBoardSummary(BOARD))
        .thenReturn(new BoardService.BoardSummary(BOARD, "Board A", false));
    when(boardService.requireBoardSummary(BOARD_B))
        .thenReturn(new BoardService.BoardSummary(BOARD_B, "Board B", false));
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Ready", 1));
    when(boardService.requireColumn(30L, BOARD_B)).thenReturn(column(30L, "Done", 4));

    List<CardService.CardSearchHit> hits = service.searchByNumber(5L, 42);

    assertThat(hits)
        .extracting(CardService.CardSearchHit::projectId)
        .containsExactly(PROJECT, PROJECT_B);
    assertThat(hits)
        .extracting(CardService.CardSearchHit::projectName)
        .containsExactly("Projekt A", "Projekt B");
    assertThat(hits)
        .extracting(CardService.CardSearchHit::boardName)
        .containsExactly("Board A", "Board B");
    assertThat(hits)
        .extracting(CardService.CardSearchHit::columnName)
        .containsExactly("Ready", "Done");
  }

  @Test
  void searchByNumber_asksOnlyForProjectsOfCaller() {
    // Sicherheitskern: gesucht wird ausschliesslich in den Projekten des Aufrufers. Eine Nummer,
    // die nur in einem fremden Projekt existiert, kann deshalb gar nicht erst auftauchen — sie
    // ist von einer nirgends existierenden Nummer nicht unterscheidbar.
    when(projects.listAccessible(5L)).thenReturn(List.of(accessible(PROJECT, "Projekt A")));
    when(cards.findByNumberInProjects(42, List.of(PROJECT))).thenReturn(List.of());

    assertThat(service.searchByNumber(5L, 42)).isEmpty();

    verify(cards).findByNumberInProjects(42, List.of(PROJECT));
  }

  @Test
  void searchByNumber_returnsEmpty_withoutQuery_whenCallerHasNoProjects() {
    when(projects.listAccessible(5L)).thenReturn(List.of());

    assertThat(service.searchByNumber(5L, 42)).isEmpty();

    verify(cards, never()).findByNumberInProjects(anyInt(), anyList());
  }

  @Test
  void searchByNumber_returnsHitWithoutBoardAndColumn_forPoolIdea() {
    when(projects.listAccessible(5L)).thenReturn(List.of(accessible(PROJECT, "Projekt A")));
    when(cards.findByNumberInProjects(7, List.of(PROJECT))).thenReturn(List.of(poolIdea(1L)));

    List<CardService.CardSearchHit> hits = service.searchByNumber(5L, 7);

    assertThat(hits).singleElement().isNotNull();
    CardService.CardSearchHit hit = hits.get(0);
    assertThat(hit.projectName()).isEqualTo("Projekt A");
    assertThat(hit.boardId()).isNull();
    assertThat(hit.boardName()).isNull();
    assertThat(hit.boardArchived()).isFalse();
    assertThat(hit.columnId()).isNull();
    assertThat(hit.columnName()).isNull();
    // Eine board-lose Pool-Idee loest weder Board noch Spalte auf.
    verify(boardService, never()).requireBoardSummary(anyLong());
    verify(boardService, never()).requireColumn(anyLong(), anyLong());
  }

  @Test
  void searchByNumber_reportsArchivedBoard_withName() {
    // Die Karte bleibt auffindbar, obwohl das Board ueber die normale Board-API 404 liefert.
    when(projects.listAccessible(5L)).thenReturn(List.of(accessible(PROJECT, "Projekt A")));
    when(cards.findByNumberInProjects(42, List.of(PROJECT)))
        .thenReturn(List.of(cardIn(1L, PROJECT, BOARD, 20L, 42)));
    when(boardService.requireBoardSummary(BOARD))
        .thenReturn(new BoardService.BoardSummary(BOARD, "Altes Board", true));
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Done", 4));

    CardService.CardSearchHit hit = service.searchByNumber(5L, 42).get(0);

    assertThat(hit.boardName()).isEqualTo("Altes Board");
    assertThat(hit.boardArchived()).isTrue();
    assertThat(hit.columnName()).isEqualTo("Done");
  }

  // --- Board-lose Pool-Ideen editierbar (#405) --------------------------

  @Test
  void update_onBoardlessPoolIdea_editsViaProjectRight_andSkipsBoardEvent() {
    // Board-lose Idee: Recht projekt-basiert (card.projectId()), kein Board-Live-Update.
    when(cards.findById(1L)).thenReturn(Optional.of(poolIdea(1L)));

    CardService.CardView view = service.update(9L, 1L, "Neu", null, null, null, null, null);

    verify(permissions).require(9L, PROJECT, Permission.TICKET_UPDATE);
    verify(activity)
        .add(
            1L,
            9L,
            CardActivityType.UPDATED,
            "Karte bearbeitet",
            FIXED,
            ActorContext.ActorStamp.unknown());
    verify(events, never()).publishEvent(any(CardBoardActivityEvent.class));
    assertThat(view.title()).isEqualTo("Neu");
    assertThat(view.boardId()).isNull();
  }

  @Test
  void update_onBoardlessPoolIdea_setsDueDate() {
    // Fälligkeit an einer board-losen Idee editierbar.
    when(cards.findById(1L)).thenReturn(Optional.of(poolIdea(1L)));
    Instant due = Instant.parse("2026-03-01T00:00:00Z");

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.update(9L, 1L, "Neu", null, null, null, null, due);

    verify(cards).save(captor.capture());
    assertThat(captor.getValue().dueDate()).isEqualTo(due);
  }

  @Test
  void setAssignees_onBoardlessPoolIdea_worksViaProjectRight_andSkipsBoardEvent() {
    when(cards.findById(1L)).thenReturn(Optional.of(poolIdea(1L)));
    when(permissions.isRealProjectMember(7L, PROJECT)).thenReturn(true);

    service.setAssignees(3L, 1L, List.of(7L));

    verify(permissions).require(3L, PROJECT, Permission.TICKET_UPDATE);
    verify(assignees).replaceAssignees(1L, List.of(7L));
    verify(activity)
        .add(
            1L,
            3L,
            CardActivityType.ASSIGNED,
            "Zuständige geändert",
            FIXED,
            ActorContext.ActorStamp.unknown());
    verify(events, never()).publishEvent(any(CardBoardActivityEvent.class));
  }

  @Test
  void listActivity_onBoardlessPoolIdea_checksMembershipViaProject() {
    when(cards.findById(1L)).thenReturn(Optional.of(poolIdea(1L)));
    when(activity.findByCardId(1L)).thenReturn(List.of());

    service.listActivity(5L, 1L);

    verify(permissions).requireMembership(5L, PROJECT);
  }

  // --- Modul-Fassade fuer fremde Module (#458) --------------------------

  /** Board-gebundene Karte mit frei waehlbarer Position, Sichtbarkeit und Typ. */
  private static Card boardCard(
      long id, long columnId, int number, int position, boolean archived, boolean ideaStored) {
    return new Card(
        id,
        BOARD,
        columnId,
        number,
        "Titel",
        "Body",
        position,
        archived,
        ideaStored,
        null,
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
        null);
  }

  /** Legacy-Pool-Idee aus der Zeit vor #402: board-los und ohne projektweite Nummer. */
  private static Card pooledIdeaWithoutNumber(long id) {
    return new Card(
        id,
        null, // boardId
        null, // columnId
        null, // number
        "Titel",
        "Body",
        0, // positionInColumn
        false,
        true,
        null, // movedToDoneAt
        1L, // createdBy
        FIXED,
        FIXED,
        CardType.CARD,
        null, // parentId
        null, // shortcode
        null, // dueDate
        PROJECT,
        null, // targetBoardId
        null,
        null); // externalKey
  }

  @Test
  void listBoardItems_requiresMembershipInBoardProject() {
    when(cards.findByBoardId(BOARD)).thenReturn(List.of());

    service.listBoardItems(5L, BOARD);

    verify(permissions).requireMembership(5L, PROJECT);
  }

  @Test
  void listBoardItems_throwsBoardNotFound_whenBoardUnknown() {
    when(boardService.requireProjectId(BOARD)).thenThrow(new BoardNotFoundException());

    assertThatThrownBy(() -> service.listBoardItems(5L, BOARD))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void listBoardItems_skipsArchivedCards() {
    when(cards.findByBoardId(BOARD)).thenReturn(List.of(boardCard(1L, 20L, 1, 0, true, false)));

    assertThat(service.listBoardItems(5L, BOARD)).isEmpty();
  }

  @Test
  void listBoardItems_skipsIdeaStoredCards() {
    // #434: im Ideen-Speicher liegende Karten tragen weiter Board und Spalte, sind fuer Menschen
    // aber ausgeblendet — die Automatik darf sie folglich auch nicht sehen.
    when(cards.findByBoardId(BOARD)).thenReturn(List.of(boardCard(2L, 20L, 1, 0, false, true)));

    assertThat(service.listBoardItems(5L, BOARD)).isEmpty();
  }

  @Test
  void listBoardItems_sortsByPositionInColumn() {
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                boardCard(1L, 20L, 1, 2, false, false),
                boardCard(2L, 20L, 2, 0, false, false),
                boardCard(3L, 20L, 3, 1, false, false)));

    assertThat(service.listBoardItems(5L, BOARD))
        .extracting(CardService.BoardItemView::id)
        .containsExactly(2L, 3L, 1L);
  }

  @Test
  void listBoardItems_projectsCardFieldsIntoView() {
    when(cards.findByBoardId(BOARD)).thenReturn(List.of(boardCard(1L, 20L, 7, 3, false, false)));

    assertThat(service.listBoardItems(5L, BOARD))
        .singleElement()
        .extracting(
            CardService.BoardItemView::id,
            CardService.BoardItemView::number,
            CardService.BoardItemView::title,
            CardService.BoardItemView::description,
            CardService.BoardItemView::columnId,
            CardService.BoardItemView::positionInColumn,
            CardService.BoardItemView::epic)
        .containsExactly(1L, 7, "Titel", "Body", 20L, 3, false);
  }

  @Test
  void listBoardItems_marksEpicsAsEpic() {
    // Epics gehoeren zur Item-Liste (anders als bei listByBoard) und muessen als solche erkennbar
    // sein, ohne den Kartentyp aus card.domain nach aussen zu geben.
    when(cards.findByBoardId(BOARD))
        .thenReturn(List.of(card(5L, 20L, 3, false, null, CardType.EPIC, null, "E")));

    assertThat(service.listBoardItems(5L, BOARD))
        .singleElement()
        .extracting(CardService.BoardItemView::epic)
        .isEqualTo(true);
  }

  // --- createProjectIdea mit externalKey (#534) ------------------------

  @Test
  void createProjectIdea_withExternalKey_persistsKeyOnNewCard() {
    when(cards.allocateCardNumber(PROJECT)).thenReturn(9);

    CardService.IdeaCreation result =
        service.createProjectIdea(1L, PROJECT, "Finding", null, BOARD, "sonar:abc", null);

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().externalKey()).isEqualTo("sonar:abc");
    assertThat(result.created()).isTrue();
  }

  @Test
  void createProjectIdea_withExistingExternalKey_returnsExistingWithoutCreating() {
    // Duplikat: bestehende Karte zurück, nichts anlegen, kein Aktivitätseintrag, kein SSE-Event.
    when(cards.findByProjectIdAndExternalKey(PROJECT, "sonar:abc"))
        .thenReturn(Optional.of(boardCard(7L, 20L, 3, 0, false, false)));

    CardService.IdeaCreation result =
        service.createProjectIdea(1L, PROJECT, "Finding", null, BOARD, "sonar:abc", null);

    assertThat(result.created()).isFalse();
    assertThat(result.view().id()).isEqualTo(7L);
    verify(cards, never()).save(any(Card.class));
    verify(activity, never()).add(anyLong(), anyLong(), any(), any(), any(), any());
    verify(events, never()).publishEvent(any(ProjectIdeasChangedEvent.class));
  }

  @Test
  void createDirect_createsBoardCardWithExternalKey() {
    // #535: direct-Ingest läuft über den normalen Anlege-Pfad und persistiert den Schlüssel.
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(9);

    CardService.IdeaCreation result =
        service.createDirect(1L, BOARD, 20L, "Finding", null, "sonar:abc", null, null);

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().externalKey()).isEqualTo("sonar:abc");
    assertThat(captor.getValue().boardId()).isEqualTo(BOARD);
    assertThat(result.created()).isTrue();
  }

  @Test
  void createDirect_withExistingExternalKey_returnsExistingWithoutCreating() {
    when(cards.findByProjectIdAndExternalKey(PROJECT, "sonar:abc"))
        .thenReturn(Optional.of(boardCard(7L, 20L, 3, 0, false, false)));

    CardService.IdeaCreation result =
        service.createDirect(1L, BOARD, 20L, "Finding", null, "sonar:abc", null, null);

    assertThat(result.created()).isFalse();
    assertThat(result.view().id()).isEqualTo(7L);
    verify(cards, never()).save(any(Card.class));
  }

  @Test
  void replaceDependenciesFromIngest_storesUnknownNumbers() {
    // #566: Der Import darf auf Karten verweisen, die noch nicht angekommen sind — sonst waere die
    // Importreihenfolge bindend. Die DB traegt das (kein Fremdschluessel).
    when(cards.findById(7L)).thenReturn(Optional.of(boardCard(7L, 20L, 3, 0, false, false)));

    service.replaceDependenciesFromIngest(1L, 7L, PROJECT, List.of(99, 1234));

    verify(dependencies).replaceDependencies(7L, List.of(99, 1234));
  }

  @Test
  void replaceDependenciesFromIngest_rejectsSelfReference() {
    // Die Selbstverweis-Pruefung bleibt geteilt: sie haengt nicht am Wissen ueber andere Karten.
    when(cards.findById(7L)).thenReturn(Optional.of(boardCard(7L, 20L, 3, 0, false, false)));

    assertThatThrownBy(() -> service.replaceDependenciesFromIngest(1L, 7L, PROJECT, List.of(3)))
        .isInstanceOf(InvalidDependencyException.class);
    verify(dependencies, never()).replaceDependencies(anyLong(), anyList());
  }

  @Test
  void replaceDependenciesFromIngest_deduplicates() {
    when(cards.findById(7L)).thenReturn(Optional.of(boardCard(7L, 20L, 3, 0, false, false)));

    service.replaceDependenciesFromIngest(1L, 7L, PROJECT, List.of(99, 99, 100));

    verify(dependencies).replaceDependencies(7L, List.of(99, 100));
  }

  @Test
  void replaceDependenciesFromIngest_clearsOnEmptyList() {
    when(cards.findById(7L)).thenReturn(Optional.of(boardCard(7L, 20L, 3, 0, false, false)));

    service.replaceDependenciesFromIngest(1L, 7L, PROJECT, List.of());

    verify(dependencies).replaceDependencies(7L, List.of());
  }

  @Test
  void replaceDependenciesFromIngest_clearsOnNull() {
    when(cards.findById(7L)).thenReturn(Optional.of(boardCard(7L, 20L, 3, 0, false, false)));

    service.replaceDependenciesFromIngest(1L, 7L, PROJECT, null);

    verify(dependencies).replaceDependencies(7L, List.of());
  }

  @Test
  void replaceDependenciesFromIngest_rejectsCardWithoutNumber_alsoForEmptyList() {
    // Die Nummern-Pruefung steht vor jeder Listenbehandlung (Code-Review Codex, Fund 1): Eine
    // Karte ohne Nummer ist kein gueltiges Ziel, auch nicht zum Loeschen. Ein stilles 204
    // verspraeche eine Operation, die es fuer sie nicht gibt.
    when(cards.findById(7L)).thenReturn(Optional.of(pooledIdeaWithoutNumber(7L)));

    assertThatThrownBy(() -> service.replaceDependenciesFromIngest(1L, 7L, PROJECT, List.of()))
        .isInstanceOf(CardWithoutNumberException.class);
    verify(dependencies, never()).replaceDependencies(anyLong(), anyList());
  }

  @Test
  void replaceDependenciesFromIngest_rejectsCardWithoutNumber_alsoForNull() {
    when(cards.findById(7L)).thenReturn(Optional.of(pooledIdeaWithoutNumber(7L)));

    assertThatThrownBy(() -> service.replaceDependenciesFromIngest(1L, 7L, PROJECT, null))
        .isInstanceOf(CardWithoutNumberException.class);
    verify(dependencies, never()).replaceDependencies(anyLong(), anyList());
  }

  @Test
  void replaceDependenciesFromIngest_rejectsCardWithoutNumber() {
    // Legacy-Pool-Idee ohne Nummer: definierte Antwort statt Exception aus requireNumber().
    when(cards.findById(7L)).thenReturn(Optional.of(pooledIdeaWithoutNumber(7L)));

    assertThatThrownBy(() -> service.replaceDependenciesFromIngest(1L, 7L, PROJECT, List.of(99)))
        .isInstanceOf(CardWithoutNumberException.class);
    verify(dependencies, never()).replaceDependencies(anyLong(), anyList());
  }

  @Test
  void replaceDependenciesFromIngest_rejectsCardOfOtherProject() {
    // Der Token bindet an ein Projekt; eine Karte aus einem fremden bleibt unerreichbar.
    when(cards.findById(7L)).thenReturn(Optional.of(boardCard(7L, 20L, 3, 0, false, false)));

    assertThatThrownBy(
            () -> service.replaceDependenciesFromIngest(1L, 7L, PROJECT + 1, List.of(99)))
        .isInstanceOf(CardNotFoundException.class);
    verify(dependencies, never()).replaceDependencies(anyLong(), anyList());
  }

  @Test
  void replaceDependenciesFromIngest_throwsForUnknownCard() {
    when(cards.findById(7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.replaceDependenciesFromIngest(1L, 7L, PROJECT, List.of(99)))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void createDirect_withGivenNumber_usesItInsteadOfAllocating() {
    // #565: Die vorgegebene Nummer ersetzt die Vergabe — die Identität der migrierten Karte
    // bleibt erhalten.
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    CardService.IdeaCreation result =
        service.createDirect(1L, BOARD, 20L, "Migriert", null, "github#278", 278, null);

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().number()).isEqualTo(278);
    assertThat(result.created()).isTrue();
    verify(cards, never()).allocateCardNumber(anyLong());
  }

  @Test
  void createDirect_withGivenNumber_holdsLockBeforeChecking() {
    // Die Sperre bleibt, obwohl die Berechnung entfällt: sonst kollidiert ein Import mit einer
    // laufenden Anlage genau dann, wenn beide dieselbe Zahl treffen.
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    service.createDirect(1L, BOARD, 20L, "Migriert", null, "github#278", 278, null);

    InOrder order = inOrder(cards);
    order.verify(cards).lockCardNumbers(PROJECT);
    order.verify(cards).hasCardWithoutExternalKey(PROJECT);
    order.verify(cards).isNumberTaken(PROJECT, 278);
    order.verify(cards).save(any(Card.class));
  }

  @Test
  void createDirect_withTakenNumber_throwsConflict() {
    when(cards.isNumberTaken(PROJECT, 278)).thenReturn(true);

    assertThatThrownBy(() -> service.createDirect(1L, BOARD, 20L, "Migriert", null, "k", 278, null))
        .isInstanceOf(CardNumberConflictException.class);
    verify(cards, never()).save(any(Card.class));
  }

  @Test
  void createDirect_withGivenNumber_throwsConflict_whenProjectHasImportForeignCard() {
    // Vorbedingung: in ein gewachsenes Projekt wird nicht hineinimportiert.
    when(cards.hasCardWithoutExternalKey(PROJECT)).thenReturn(true);

    assertThatThrownBy(() -> service.createDirect(1L, BOARD, 20L, "Migriert", null, "k", 278, null))
        .isInstanceOf(CardNumberConflictException.class);
    verify(cards, never()).save(any(Card.class));
  }

  @Test
  void createDirect_withoutGivenNumber_skipsPreconditionAndAllocates() {
    // Ohne vorgegebene Nummer bleibt der Pfad unverändert — kein Vorbedingungs-Check.
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(9);

    service.createDirect(1L, BOARD, 20L, "Karte", null, null, null, null);

    verify(cards).allocateCardNumber(PROJECT);
    verify(cards, never()).hasCardWithoutExternalKey(anyLong());
    verify(cards, never()).isNumberTaken(anyLong(), anyInt());
  }

  @Test
  void createDirect_existingKeyWithDifferentNumber_throwsConflict() {
    // Der Idempotenz-Treffer darf keine andere Identität zurückgeben als angefordert.
    when(cards.findByProjectIdAndExternalKey(PROJECT, "github#278"))
        .thenReturn(Optional.of(boardCard(7L, 20L, 3, 0, false, false)));

    assertThatThrownBy(
            () -> service.createDirect(1L, BOARD, 20L, "Migriert", null, "github#278", 278, null))
        .isInstanceOf(CardNumberConflictException.class);
    verify(cards, never()).save(any(Card.class));
  }

  @Test
  void createDirect_existingKeyWithSameNumber_returnsExistingWithoutCreating() {
    when(cards.findByProjectIdAndExternalKey(PROJECT, "github#3"))
        .thenReturn(Optional.of(boardCard(7L, 20L, 3, 0, false, false)));

    CardService.IdeaCreation result =
        service.createDirect(1L, BOARD, 20L, "Migriert", null, "github#3", 3, null);

    assertThat(result.created()).isFalse();
    assertThat(result.view().id()).isEqualTo(7L);
    verify(cards, never()).save(any(Card.class));
  }

  @Test
  void createDirect_withoutExternalKey_skipsLookup() {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(9);

    CardService.IdeaCreation result =
        service.createDirect(1L, BOARD, 20L, "Karte", null, null, null, null);

    assertThat(result.created()).isTrue();
    verify(cards, never()).findByProjectIdAndExternalKey(anyLong(), any());
  }

  @Test
  void createDirect_checksPermissionBeforeDuplicateLookup() {
    // Rechte vor dem Duplikat-Check: kein Existenz-Leak an Unberechtigte.
    doThrow(new ProjectNotFoundException())
        .when(permissions)
        .require(1L, PROJECT, Permission.TICKET_CREATE);

    assertThatThrownBy(
            () -> service.createDirect(1L, BOARD, 20L, "F", null, "sonar:abc", null, null))
        .isInstanceOf(ProjectNotFoundException.class);
    verify(cards, never()).findByProjectIdAndExternalKey(anyLong(), any());
  }

  @Test
  void createProjectIdea_withoutExternalKey_skipsLookup() {
    when(cards.allocateCardNumber(PROJECT)).thenReturn(9);

    service.createProjectIdea(1L, PROJECT, "Idee", null, BOARD, null, null);

    verify(cards, never()).findByProjectIdAndExternalKey(anyLong(), any());
  }

  @Test
  void requireProjectId_returnsProjectOfCard() {
    when(cards.findById(1L)).thenReturn(Optional.of(boardCard(1L, 20L, 1, 0, false, false)));

    assertThat(service.requireProjectId(1L)).isEqualTo(PROJECT);
  }

  @Test
  void requireProjectId_worksForBoardlessPoolIdea() {
    // #405: eine board-lose Pool-Idee traegt keine Board-ID, aber immer eine Projekt-ID — genau
    // deshalb loest die Fassade ueber die Karte auf und nicht ueber deren Board.
    when(cards.findById(1L)).thenReturn(Optional.of(poolIdea(1L)));

    assertThat(service.requireProjectId(1L)).isEqualTo(PROJECT);
  }

  @Test
  void requireProjectId_throwsCardNotFound_whenCardUnknown() {
    when(cards.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.requireProjectId(1L))
        .isInstanceOf(CardNotFoundException.class);
  }

  // --- getCard ---------------------------------------------------------

  @Test
  void getCard_returnsViewOfCard() {
    when(cards.findById(1L)).thenReturn(Optional.of(boardCard(1L, 20L, 7, 0, false, false)));

    assertThat(service.getCard(5L, 1L)).extracting(CardService.CardView::number).isEqualTo(7);
  }

  @Test
  void getCard_requiresMembershipInCardsProject() {
    when(cards.findById(1L)).thenReturn(Optional.of(boardCard(1L, 20L, 7, 0, false, false)));

    service.getCard(5L, 1L);

    verify(permissions).requireMembership(5L, PROJECT);
  }

  @Test
  void getCard_liefertDieHerkunftAlsNummerDesVorfahren() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(boardCard(1L, 20L, 7, 0, false, false).withDerivedFrom(91L)));
    when(cards.findById(91L))
        .thenReturn(Optional.of(card(91L, 20L, 42, false, null, CardType.CARD, null, null)));

    assertThat(service.getCard(5L, 1L).derivedFrom()).isEqualTo(42);
  }

  @Test
  void getCard_liefertNull_wennDerVorfahrNichtMehrExistiert() {
    // Regulaer raeumt ON DELETE SET NULL das auf; die Sicht haelt den Zustand trotzdem aus.
    when(cards.findById(1L))
        .thenReturn(Optional.of(boardCard(1L, 20L, 7, 0, false, false).withDerivedFrom(91L)));
    when(cards.findById(91L)).thenReturn(Optional.empty());

    assertThat(service.getCard(5L, 1L).derivedFrom()).isNull();
  }

  @Test
  void getCard_liefertNull_ohneHerkunft() {
    when(cards.findById(1L)).thenReturn(Optional.of(boardCard(1L, 20L, 7, 0, false, false)));

    assertThat(service.getCard(5L, 1L).derivedFrom()).isNull();
  }

  @Test
  void listBoardItems_liefertNull_wennDerVorfahrNichtInDerSammelantwortSteht() {
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null).withDerivedFrom(91L)));
    when(cards.findByIds(any())).thenReturn(List.of());

    assertThat(service.listBoardItems(5L, BOARD))
        .singleElement()
        .extracting(CardService.BoardItemView::derivedFrom)
        .isNull();
  }

  @Test
  void getCard_throwsCardNotFound_whenCardUnknown() {
    when(cards.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getCard(5L, 1L)).isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void getCard_propagatesNotFound_whenCallerIsNoMember() {
    // Nichtmitglied wie unbekannte Karte → 404, kein Existenz-Leak.
    when(cards.findById(1L)).thenReturn(Optional.of(boardCard(1L, 20L, 7, 0, false, false)));
    doThrow(new ProjectNotFoundException()).when(permissions).requireMembership(5L, PROJECT);

    assertThatThrownBy(() -> service.getCard(5L, 1L)).isInstanceOf(ProjectNotFoundException.class);
  }

  @Test
  void requireOnBoard_passes_whenCardIsOnBoard() {
    when(cards.findById(1L)).thenReturn(Optional.of(boardCard(1L, 20L, 1, 0, false, false)));

    assertThatCode(() -> service.requireOnBoard(1L, BOARD)).doesNotThrowAnyException();
  }

  @Test
  void requireOnBoard_throwsCardNotFound_whenCardUnknown() {
    when(cards.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.requireOnBoard(1L, BOARD))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void requireOnBoard_throwsCardNotFound_whenCardOnOtherBoard() {
    Card otherBoard =
        new Card(
            1L,
            99L,
            20L,
            1,
            "T",
            null,
            0,
            false,
            false,
            null,
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
            null);
    when(cards.findById(1L)).thenReturn(Optional.of(otherBoard));

    assertThatThrownBy(() -> service.requireOnBoard(1L, BOARD))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void requireOnBoard_throwsCardNotFound_whenCardIsBoardless() {
    // Board-lose Pool-Idee: boardId == null darf nicht als Treffer durchgehen (NPE-frei).
    when(cards.findById(1L)).thenReturn(Optional.of(poolIdea(1L)));

    assertThatThrownBy(() -> service.requireOnBoard(1L, BOARD))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void requireOnBoard_throwsCardNotFound_whenCardIsIdeaStored() {
    // #434: auf dem richtigen Board, aber im Ideen-Speicher — fuer die Automatik nicht vorhanden.
    when(cards.findById(1L)).thenReturn(Optional.of(boardCard(1L, 20L, 1, 0, false, true)));

    assertThatThrownBy(() -> service.requireOnBoard(1L, BOARD))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void requireOnBoard_comparesBoardIdsByValue_beyondLongCache() {
    // Board-IDs jenseits des Long-Caches (> 127): ein Referenzvergleich ('!=') wuerde hier
    // faelschlich CardNotFound werfen und den kanbancompat-Zugriff auf grossen Boards zerlegen.
    long largeBoard = 5000L;
    Card onLargeBoard =
        new Card(
            1L,
            largeBoard,
            20L,
            1,
            "T",
            null,
            0,
            false,
            false,
            null,
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
            null);
    when(cards.findById(1L)).thenReturn(Optional.of(onLargeBoard));

    assertThatCode(() -> service.requireOnBoard(1L, largeBoard)).doesNotThrowAnyException();
  }
}
