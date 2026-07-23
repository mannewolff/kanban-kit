package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.mwolff.manban.board.application.BoardChangedEvent;
import org.mwolff.manban.board.application.BoardChangedEvent.ChangeType;
import org.mwolff.manban.board.application.BoardColumnRepository;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardRepository;
import org.mwolff.manban.board.application.ColumnNotFoundException;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.board.domain.BoardColumn;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.card.domain.Label;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.springframework.context.ApplicationEventPublisher;

/** Verhaltenstests der Karten- und Epic-Use-Cases (Mockito an den Ports). */
// PMD.TooManyMethods: umfassende Unit-Suite (Karten + Epics, Erfolgs- und Fehlerpfade je
// Use-Case). Viele kleine @Test-Methoden sind hier gewollt, kein God-Class-Smell.
// PMD.ExcessiveImports: die Suite deckt viele Ports/Domänentypen ab (inkl. der Live-Board-Events);
// die Import-Zahl folgt aus dem breiten Testumfang, kein Kopplungs-Smell.
@SuppressWarnings({
  "PMD.TooManyMethods",
  "PMD.CyclomaticComplexity",
  "PMD.CouplingBetweenObjects",
  "PMD.ExcessiveImports"
})
class CardServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;

  private CardRepository cards;
  private CardDependencyRepository dependencies;
  private BoardRepository boards;
  private BoardColumnRepository columns;
  private PermissionChecker permissions;
  private CardColumnTransitionRepository transitions;
  private CardAssigneeRepository assignees;
  private ProjectMembershipRepository memberships;
  private LabelRepository labels;
  private CardLabelRepository cardLabels;
  private CardActivityRepository activity;
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
        type, parentId, shortcode, null, PROJECT, null);
  }

  private static BoardColumn column(long id, String name, int position) {
    return new BoardColumn(id, BOARD, name, position, null);
  }

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    dependencies = mock(CardDependencyRepository.class);
    boards = mock(BoardRepository.class);
    columns = mock(BoardColumnRepository.class);
    permissions = mock(PermissionChecker.class);
    transitions = mock(CardColumnTransitionRepository.class);
    assignees = mock(CardAssigneeRepository.class);
    memberships = mock(ProjectMembershipRepository.class);
    labels = mock(LabelRepository.class);
    cardLabels = mock(CardLabelRepository.class);
    activity = mock(CardActivityRepository.class);
    events = mock(ApplicationEventPublisher.class);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    service =
        new CardService(
            cards,
            dependencies,
            boards,
            columns,
            permissions,
            transitions,
            assignees,
            memberships,
            labels,
            cardLabels,
            activity,
            events,
            clock);
    when(boards.findById(BOARD)).thenReturn(Optional.of(new Board(BOARD, 1L, "B", FIXED)));
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
        c.targetBoardId());
  }

  // --- create -----------------------------------------------------------

  @Test
  void create_setsCreatedAtFromInjectedClock() {
    // Given
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));
    when(cards.nextCardNumber(PROJECT)).thenReturn(1);
    when(cards.maxActivePositionInColumn(20L)).thenReturn(-1);

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.create(1L, BOARD, 20L, "Titel", null, null, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().createdAt()).isEqualTo(FIXED);
  }

  @Test
  void create_setsDueDate_whenProvided() {
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));
    when(cards.nextCardNumber(PROJECT)).thenReturn(1);
    when(cards.maxActivePositionInColumn(20L)).thenReturn(-1);
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
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));
    when(cards.nextCardNumber(PROJECT)).thenReturn(1);
    when(cards.maxActivePositionInColumn(20L)).thenReturn(-1);
    when(memberships.findByProjectIdAndUserId(1L, 7L))
        .thenReturn(Optional.of(mock(ProjectMembership.class)));
    when(memberships.findByProjectIdAndUserId(1L, 8L))
        .thenReturn(Optional.of(mock(ProjectMembership.class)));

    service.create(
        1L, BOARD, 20L, "Titel", null, null, null, false, null, List.of(7L, 8L, 7L), null);

    verify(assignees).replaceAssignees(1L, List.of(7L, 8L));
    // Genau ein Aktivitätseintrag (CREATED) — kein zusätzlicher ASSIGNED beim atomaren Anlegen.
    verify(activity).add(1L, 1L, CardActivityType.CREATED, "Karte angelegt", FIXED);
    verify(activity, times(1)).add(anyLong(), anyLong(), any(), any(), any());
  }

  @Test
  void create_ignoresEmptyAssignees() {
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));
    when(cards.nextCardNumber(PROJECT)).thenReturn(1);
    when(cards.maxActivePositionInColumn(20L)).thenReturn(-1);

    service.create(1L, BOARD, 20L, "Titel", null, null, null, false, null, List.of(), null);

    verify(assignees, never()).replaceAssignees(anyLong(), anyList());
  }

  @Test
  void create_rejectsForeignAssignee() {
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));
    when(cards.nextCardNumber(PROJECT)).thenReturn(1);
    when(cards.maxActivePositionInColumn(20L)).thenReturn(-1);
    when(memberships.findByProjectIdAndUserId(1L, 9L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.create(
                    1L, BOARD, 20L, "Titel", null, null, null, false, null, List.of(9L), null))
        .isInstanceOf(InvalidAssigneeException.class);
    verify(assignees, never()).replaceAssignees(anyLong(), anyList());
  }

  @Test
  void create_appliesLabels_whenProvided() {
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));
    when(cards.nextCardNumber(PROJECT)).thenReturn(1);
    when(cards.maxActivePositionInColumn(20L)).thenReturn(-1);
    when(labels.findByBoardId(BOARD))
        .thenReturn(
            List.of(new Label(7L, BOARD, "Bug", "#f00"), new Label(8L, BOARD, "Ux", "#0f0")));

    service.create(
        1L, BOARD, 20L, "Titel", null, null, null, false, null, null, List.of(7L, 8L, 7L));

    verify(cardLabels).replaceLabels(1L, List.of(7L, 8L));
  }

  @Test
  void create_ignoresEmptyLabels() {
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));
    when(cards.nextCardNumber(PROJECT)).thenReturn(1);
    when(cards.maxActivePositionInColumn(20L)).thenReturn(-1);

    service.create(1L, BOARD, 20L, "Titel", null, null, null, false, null, null, List.of());

    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  @Test
  void create_rejectsForeignLabel() {
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));
    when(cards.nextCardNumber(PROJECT)).thenReturn(1);
    when(cards.maxActivePositionInColumn(20L)).thenReturn(-1);
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
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));
    when(cards.nextCardNumber(PROJECT)).thenReturn(8);
    when(cards.maxActivePositionInColumn(20L)).thenReturn(-1);

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
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));
    when(cards.nextCardNumber(PROJECT)).thenReturn(1);
    when(cards.maxActivePositionInColumn(20L)).thenReturn(4);

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
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));

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
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));
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
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));
    when(cards.nextCardNumber(PROJECT)).thenReturn(5);
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
    when(boards.findById(BOARD)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.create(1L, BOARD, 20L, "Titel", null, null, null))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void create_throwsColumnNotFound_whenColumnUnknown() {
    // Given
    when(columns.findById(20L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.create(1L, BOARD, 20L, "Titel", null, null, null))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  @Test
  void create_throwsColumnNotFound_whenColumnOnOtherBoard() {
    // Given
    when(columns.findById(20L))
        .thenReturn(Optional.of(new BoardColumn(20L, 99L, "Backlog", 0, null)));

    // When / Then
    assertThatThrownBy(() -> service.create(1L, BOARD, 20L, "Titel", null, null, null))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  @Test
  void create_throwsInvalidDependency_whenParentIsNotEpic() {
    // Given
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));
    when(cards.findById(30L))
        .thenReturn(Optional.of(card(30L, 20L, 5, false, null, CardType.CARD, null, null)));

    // When / Then
    assertThatThrownBy(() -> service.create(1L, BOARD, 20L, "Titel", null, null, 30L))
        .isInstanceOf(InvalidDependencyException.class);
  }

  @Test
  void create_throwsCardNotFound_whenParentUnknown() {
    // Given
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));
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
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));
    when(cards.nextCardNumber(PROJECT)).thenReturn(1);
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
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));
    when(cards.nextCardNumber(PROJECT)).thenReturn(1);
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
    when(columns.findByBoardId(BOARD))
        .thenReturn(List.of(column(20L, "Backlog", 0), column(21L, "Done", 1)));
    when(cards.nextCardNumber(PROJECT)).thenReturn(1);

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
    when(columns.findByBoardId(BOARD)).thenReturn(List.of(column(20L, "Backlog", 0)));
    when(cards.nextCardNumber(PROJECT)).thenReturn(1);

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
    when(boards.findById(BOARD)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.createEpic(1L, BOARD, "Epic", null, null))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void createEpic_throwsColumnNotFound_whenBoardHasNoColumns() {
    // Given
    when(columns.findByBoardId(BOARD)).thenReturn(List.of());

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
    when(boards.findById(BOARD)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.listByBoard(1L, BOARD))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void listEpics_countsDoneChildren() {
    // Given
    when(columns.findByBoardId(BOARD))
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
    when(boards.findById(BOARD)).thenReturn(Optional.empty());

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
    when(columns.findById(21L)).thenReturn(Optional.of(column(21L, "Done", 4)));

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
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));

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
    when(columns.findById(21L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.move(1L, 1L, 21L, 0))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  @Test
  void move_throwsColumnNotFound_whenTargetOnOtherBoard() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(columns.findById(21L)).thenReturn(Optional.of(new BoardColumn(21L, 99L, "Done", 4, null)));

    // When / Then
    assertThatThrownBy(() -> service.move(1L, 1L, 21L, 0))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  @Test
  void move_throwsBoardNotFound_whenBoardUnknown() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(boards.findById(BOARD)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.move(1L, 1L, 21L, 0))
        .isInstanceOf(BoardNotFoundException.class);
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
  void archive_throwsBoardNotFound_whenBoardUnknown() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(boards.findById(BOARD)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.archive(1L, 1L)).isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void restore_appendsAtNextPosition() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, true, null, CardType.CARD, null, null)));
    when(cards.maxActivePositionInColumn(20L)).thenReturn(2);

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.restore(1L, 1L);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().positionInColumn()).isEqualTo(3);
  }

  // --- Ideen-Speicher (Demotion / Promotion) ----------------------------

  @Test
  void moveToIdeaStorage_marksCardIdeaStoredWithMoveRight() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    CardService.CardView view = service.moveToIdeaStorage(9L, 1L);

    // Then — Ideen-Pflege nutzt das Verschieberecht (kein Löschen), setzt ideaStored und
    // protokolliert.
    verify(permissions).require(9L, 1L, Permission.CARD_MOVE);
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().ideaStored()).isTrue();
    assertThat(view.ideaStored()).isTrue();
    verify(activity).add(1L, 9L, CardActivityType.IDEA_STORED, "In den Ideen-Speicher", FIXED);
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
    when(boards.findById(BOARD)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.moveToIdeaStorage(9L, 1L))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void promoteToBacklog_movesToFirstColumnAtEndWithMoveRight() {
    // Given: eine Idee und ein Board mit (absichtlich unsortierten) Spalten — die erste Spalte
    // ist die mit der niedrigsten Position (Backlog), nicht die erste der Liste.
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(columns.findByBoardId(BOARD))
        .thenReturn(List.of(column(21L, "Ready", 1), column(20L, "Backlog", 0)));
    when(cards.maxActivePositionInColumn(20L)).thenReturn(4);

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    CardService.CardView view = service.promoteToBacklog(9L, 1L);

    // Then — landet in der Backlog-Spalte (20) am Ende (5), nicht mehr im Ideen-Speicher.
    verify(permissions).require(9L, 1L, Permission.CARD_MOVE);
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().columnId()).isEqualTo(20L);
    assertThat(captor.getValue().positionInColumn()).isEqualTo(5);
    assertThat(captor.getValue().ideaStored()).isFalse();
    assertThat(view.ideaStored()).isFalse();
    verify(activity).add(1L, 9L, CardActivityType.PROMOTED, "Ins Backlog geholt", FIXED);
  }

  @Test
  void promoteToBacklog_rejectsEpic() {
    // Given
    when(cards.findById(5L))
        .thenReturn(Optional.of(card(5L, 20L, 5, false, null, CardType.EPIC, null, "E")));

    // When / Then
    assertThatThrownBy(() -> service.promoteToBacklog(9L, 5L))
        .isInstanceOf(InvalidDependencyException.class);
    verify(cards, never()).save(any(Card.class));
  }

  @Test
  void promoteToBacklog_throwsCardNotFound_whenUnknown() {
    // Given
    when(cards.findById(1L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.promoteToBacklog(9L, 1L))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void promoteToBacklog_throwsBoardNotFound_whenBoardUnknown() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(boards.findById(BOARD)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.promoteToBacklog(9L, 1L))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void promoteToBacklog_throwsColumnNotFound_whenBoardHasNoColumns() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(columns.findByBoardId(BOARD)).thenReturn(List.of());

    // When / Then
    assertThatThrownBy(() -> service.promoteToBacklog(9L, 1L))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  @Test
  void create_asIdea_marksIdeaStoredAndSkipsTransition() {
    // Given
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));

    // When: direkt als Idee angelegt
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    CardService.CardView view = service.create(1L, BOARD, 20L, "Idee", null, null, null, true);

    // Then — ideaStored gesetzt, keine Spalten-Transition (kein Board-Workflow), CREATED
    // protokolliert.
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().ideaStored()).isTrue();
    assertThat(view.ideaStored()).isTrue();
    verify(transitions, never()).open(anyLong(), anyLong(), any(), any());
    verify(activity).add(1L, 1L, CardActivityType.CREATED, "Karte angelegt", FIXED);
  }

  @Test
  void create_normalCard_hasIdeaStoredFalseInView() {
    // Given
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));

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
    when(columns.findByBoardId(BOARD))
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
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));
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
            null);
    when(cards.findById(30L)).thenReturn(Optional.of(epicOtherBoard));

    // When / Then
    assertThatThrownBy(() -> service.create(1L, BOARD, 20L, "Titel", null, null, 30L))
        .isInstanceOf(InvalidDependencyException.class);
  }

  @Test
  void create_clearsDependencies_whenEmptyList() {
    // Given
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));

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
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));

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
  void create_normalizesBlankDescriptionToNull() {
    // Given
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));

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
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));

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
    when(columns.findByBoardId(BOARD)).thenReturn(List.of(column(20L, "Backlog", 0)));

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
    when(columns.findById(21L)).thenReturn(Optional.of(column(21L, "Done", 4)));

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
    when(columns.findById(22L)).thenReturn(Optional.of(column(22L, null, 5)));

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
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));

    // When
    CardService.CardView view = service.create(1L, BOARD, 20L, "Titel", null, null, null);

    // Then
    assertThat(view.title()).isEqualTo("Titel");
  }

  @Test
  void createEpic_assignsNextBoardNumber() {
    // Given
    when(columns.findByBoardId(BOARD)).thenReturn(List.of(column(20L, "Backlog", 0)));
    when(cards.nextCardNumber(PROJECT)).thenReturn(5);

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
    when(columns.findByBoardId(BOARD)).thenReturn(List.of(column(20L, "Backlog", 0)));

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
    when(columns.findById(21L)).thenReturn(Optional.of(column(21L, "Done", 4)));

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
    when(columns.findById(21L)).thenReturn(Optional.of(column(21L, "Done", 4)));

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
    when(cards.maxActivePositionInColumn(20L)).thenReturn(4);

    CardService.CardView view = service.restoreFromTrash(9L, 1L);

    verify(cards).restoreFromTrash(1L, 5);
    verify(activity)
        .add(1L, 9L, CardActivityType.RESTORED, "Aus Papierkorb wiederhergestellt", FIXED);
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
    when(boards.findById(20L)).thenReturn(Optional.of(new Board(20L, 2L, "Ziel", FIXED)));
    when(columns.findById(60L))
        .thenReturn(Optional.of(new BoardColumn(60L, 20L, "Backlog", 0, null)));
    when(cards.nextCardNumber(2L)).thenReturn(8);
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
    when(boards.findById(20L)).thenReturn(Optional.of(new Board(20L, 1L, "Ziel", FIXED)));
    when(columns.findById(60L))
        .thenReturn(Optional.of(new BoardColumn(60L, 20L, "Backlog", 0, null)));

    // When
    service.transfer(1L, 100L, 20L, 60L);

    // Then: die Nummer (3) bleibt erhalten — keine Neuvergabe, kein nextCardNumber-Lookup …
    verify(cards).transfer(100L, 20L, 60L, 3);
    verify(cards, never()).nextCardNumber(anyLong());
    // … und projekt-lokale Verknüpfungen wandern mit (werden NICHT gelöscht).
    verify(dependencies, never()).deleteByCardId(anyLong());
    verify(assignees, never()).deleteByCardId(anyLong());
  }

  @Test
  void transfer_requiresOwnerInBothProjects() {
    // Given
    stubTransferScenario(null);

    // When
    service.transfer(1L, 100L, 20L, 60L);

    // Then — Quellprojekt (1) und Zielprojekt (2)
    verify(permissions).requireOwner(1L, 1L);
    verify(permissions).requireOwner(1L, 2L);
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
    when(boards.findById(20L)).thenReturn(Optional.of(new Board(20L, 2L, "Ziel", FIXED)));
    when(columns.findById(60L))
        .thenReturn(Optional.of(new BoardColumn(60L, 20L, "Backlog", 0, null)));
    when(cards.nextCardNumber(2L)).thenReturn(8);

    // When
    List<CardService.CardView> result = service.bulkTransfer(1L, List.of(100L, 101L), 20L, 60L);

    // Then — die Views je Karte werden zurückgegeben (nicht null)
    assertThat(result).extracting(CardService.CardView::id).containsExactly(100L, 101L);
    verify(cards).transfer(100L, 20L, 60L, 8);
    verify(cards).transfer(101L, 20L, 60L, 8);
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
    when(boards.findById(20L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.transfer(1L, 100L, 20L, 60L))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void transfer_throwsColumnNotFound_whenTargetColumnInOtherBoard() {
    // Given
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, null, CardType.CARD, null, null)));
    when(boards.findById(20L)).thenReturn(Optional.of(new Board(20L, 2L, "Ziel", FIXED)));
    when(columns.findById(60L))
        .thenReturn(Optional.of(new BoardColumn(60L, 99L, "Fremd", 0, null)));

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
    when(memberships.findByProjectIdAndUserId(1L, 7L))
        .thenReturn(Optional.of(mock(ProjectMembership.class)));
    when(memberships.findByProjectIdAndUserId(1L, 8L))
        .thenReturn(Optional.of(mock(ProjectMembership.class)));
    when(assignees.findByCardId(1L)).thenReturn(List.of(7L, 8L));

    CardService.CardView result = service.setAssignees(3L, 1L, List.of(7L, 8L, 7L));

    verify(permissions).require(3L, 1L, Permission.TICKET_UPDATE);
    verify(assignees).replaceAssignees(1L, List.of(7L, 8L));
    verify(activity).add(1L, 3L, CardActivityType.ASSIGNED, "Zuständige geändert", FIXED);
    assertThat(result.id()).isEqualTo(1L);
    assertThat(result.assignees()).containsExactly(7L, 8L);
  }

  @Test
  void setAssignees_rejectsNonMember() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(memberships.findByProjectIdAndUserId(1L, 7L))
        .thenReturn(Optional.of(mock(ProjectMembership.class)));
    when(memberships.findByProjectIdAndUserId(1L, 8L)).thenReturn(Optional.empty());

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
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));

    service.create(1L, BOARD, 20L, "Titel", null, null, null);

    verify(activity).add(1L, 1L, CardActivityType.CREATED, "Karte angelegt", FIXED);
  }

  @Test
  void move_recordsMovedActivity_whenColumnChanges() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(columns.findById(21L)).thenReturn(Optional.of(column(21L, "Done", 4)));

    service.move(9L, 1L, 21L, 0);

    verify(activity).add(1L, 9L, CardActivityType.MOVED, "Verschoben nach Done", FIXED);
  }

  @Test
  void update_recordsUpdatedActivity() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.update(9L, 1L, "Neu", null, null, null, null, null);

    verify(activity).add(1L, 9L, CardActivityType.UPDATED, "Karte bearbeitet", FIXED);
  }

  @Test
  void archive_recordsArchivedActivity() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.archive(9L, 1L);

    verify(activity).add(1L, 9L, CardActivityType.ARCHIVED, "Archiviert", FIXED);
  }

  @Test
  void restore_recordsRestoredActivity() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, true, null, CardType.CARD, null, null)));

    service.restore(9L, 1L);

    verify(activity).add(1L, 9L, CardActivityType.RESTORED, "Wiederhergestellt", FIXED);
  }

  @Test
  void listActivity_returnsHistoryForMember() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    CardActivity entry =
        new CardActivity(3L, 1L, 9L, CardActivityType.CREATED, "Karte angelegt", FIXED);
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
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));
    when(cards.nextCardNumber(PROJECT)).thenReturn(1);
    when(cards.maxActivePositionInColumn(20L)).thenReturn(-1);

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
    when(columns.findById(21L)).thenReturn(Optional.of(column(21L, "Done", 4)));

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
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));

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

  private BoardChangedEvent onlyPublishedEvent() {
    ArgumentCaptor<BoardChangedEvent> captor = ArgumentCaptor.forClass(BoardChangedEvent.class);
    verify(events).publishEvent(captor.capture());
    return captor.getValue();
  }

  @Test
  void create_publishesCreatedEvent() {
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));

    service.create(1L, BOARD, 20L, "Titel", null, null, null);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new BoardChangedEvent(BOARD, ChangeType.CREATED, 1L));
  }

  @Test
  void createEpic_publishesCreatedEvent() {
    when(columns.findByBoardId(BOARD)).thenReturn(List.of(column(20L, "Backlog", 0)));

    service.createEpic(1L, BOARD, "Epic", null, "E");

    assertThat(onlyPublishedEvent())
        .isEqualTo(new BoardChangedEvent(BOARD, ChangeType.CREATED, 1L));
  }

  @Test
  void update_publishesUpdatedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.update(1L, 1L, "Neu", null, null, null, null, null);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new BoardChangedEvent(BOARD, ChangeType.UPDATED, 1L));
  }

  @Test
  void setAssignees_publishesUpdatedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.setAssignees(3L, 1L, List.of());

    assertThat(onlyPublishedEvent())
        .isEqualTo(new BoardChangedEvent(BOARD, ChangeType.UPDATED, 1L));
  }

  @Test
  void setLabels_publishesUpdatedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.setLabels(3L, 1L, List.of());

    assertThat(onlyPublishedEvent())
        .isEqualTo(new BoardChangedEvent(BOARD, ChangeType.UPDATED, 1L));
  }

  @Test
  void assignParent_publishesUpdatedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.assignParent(1L, 1L, null);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new BoardChangedEvent(BOARD, ChangeType.UPDATED, 1L));
  }

  @Test
  void move_publishesMovedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(columns.findById(21L)).thenReturn(Optional.of(column(21L, "Ready", 1)));

    service.move(1L, 1L, 21L, 0);

    assertThat(onlyPublishedEvent()).isEqualTo(new BoardChangedEvent(BOARD, ChangeType.MOVED, 1L));
  }

  @Test
  void archive_publishesArchivedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.archive(1L, 1L);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new BoardChangedEvent(BOARD, ChangeType.ARCHIVED, 1L));
  }

  @Test
  void restore_publishesRestoredEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, true, null, CardType.CARD, null, null)));

    service.restore(1L, 1L);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new BoardChangedEvent(BOARD, ChangeType.RESTORED, 1L));
  }

  @Test
  void moveToIdeaStorage_publishesMovedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.moveToIdeaStorage(9L, 1L);

    assertThat(onlyPublishedEvent()).isEqualTo(new BoardChangedEvent(BOARD, ChangeType.MOVED, 1L));
  }

  @Test
  void promoteToBacklog_publishesMovedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(columns.findByBoardId(BOARD)).thenReturn(List.of(column(20L, "Backlog", 0)));

    service.promoteToBacklog(9L, 1L);

    assertThat(onlyPublishedEvent()).isEqualTo(new BoardChangedEvent(BOARD, ChangeType.MOVED, 1L));
  }

  @Test
  void delete_publishesDeletedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.delete(1L, 1L);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new BoardChangedEvent(BOARD, ChangeType.DELETED, 1L));
  }

  @Test
  void restoreFromTrash_publishesRestoredEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.restoreFromTrash(9L, 1L);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new BoardChangedEvent(BOARD, ChangeType.RESTORED, 1L));
  }

  @Test
  void purge_publishesDeletedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.purge(9L, 1L);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new BoardChangedEvent(BOARD, ChangeType.DELETED, 1L));
  }

  @Test
  void transfer_publishesMovedEventForBothBoards() {
    stubTransferScenario(null);

    service.transfer(1L, 100L, 20L, 60L);

    ArgumentCaptor<BoardChangedEvent> captor = ArgumentCaptor.forClass(BoardChangedEvent.class);
    verify(events, times(2)).publishEvent(captor.capture());
    assertThat(captor.getAllValues())
        .containsExactly(
            new BoardChangedEvent(BOARD, ChangeType.MOVED, 100L),
            new BoardChangedEvent(20L, ChangeType.MOVED, 100L));
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
        null);
  }

  @Test
  void createProjectIdea_savesBoardlessIdea_withProjectAndTargetBoard() {
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    CardService.CardView view = service.createProjectIdea(1L, PROJECT, "Idee", "d", 7L);

    verify(permissions).require(1L, PROJECT, Permission.TICKET_CREATE);
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().boardId()).isNull();
    assertThat(captor.getValue().columnId()).isNull();
    assertThat(captor.getValue().number()).isNull();
    assertThat(captor.getValue().ideaStored()).isTrue();
    assertThat(captor.getValue().projectId()).isEqualTo(PROJECT);
    assertThat(captor.getValue().targetBoardId()).isEqualTo(7L);
    verify(activity).add(1L, 1L, CardActivityType.CREATED, "Idee angelegt", FIXED);
    assertThat(view.boardId()).isNull();
    // view() muss das notierte Zielboard durchreichen — das Frontend wählt es beim Einplanen vor.
    assertThat(view.targetBoardId()).isEqualTo(7L);
  }

  @Test
  void planOntoBoard_movesIdeaIntoBacklog_assignsNumberPosition_andPublishes() {
    when(cards.findById(1L)).thenReturn(Optional.of(poolIdea(1L)));
    when(columns.findByBoardId(BOARD))
        .thenReturn(List.of(column(21L, "Ready", 1), column(20L, "Backlog", 0)));
    when(cards.nextCardNumber(PROJECT)).thenReturn(5);
    when(cards.maxActivePositionInColumn(20L)).thenReturn(2);

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
    verify(activity).add(1L, 9L, CardActivityType.PROMOTED, "Auf Board eingeplant", FIXED);
    verify(events).publishEvent(new BoardChangedEvent(BOARD, ChangeType.CREATED, 1L));
    assertThat(result.boardId()).isEqualTo(BOARD);
    assertThat(result.ideaStored()).isFalse();
  }

  @Test
  void planOntoBoard_rejectsBoardOfOtherProject() {
    when(cards.findById(1L)).thenReturn(Optional.of(poolIdea(1L)));
    when(boards.findById(BOARD)).thenReturn(Optional.of(new Board(BOARD, 99L, "B", FIXED)));

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
    verify(activity).add(1L, 9L, CardActivityType.IDEA_STORED, "Zurück in den Ideen-Pool", FIXED);
    verify(events).publishEvent(new BoardChangedEvent(BOARD, ChangeType.MOVED, 1L));
    assertThat(result.boardId()).isNull();
  }

  @Test
  void listProjectIdeas_returnsOnlyCards_forMember() {
    when(cards.findIdeasByProjectId(PROJECT))
        .thenReturn(List.of(poolIdea(1L), card(2L, 20L, 2, false, null, CardType.EPIC, null, "E")));

    List<CardService.CardView> result = service.listProjectIdeas(1L, PROJECT);

    verify(permissions).requireMembership(1L, PROJECT);
    assertThat(result).singleElement().extracting(CardService.CardView::id).isEqualTo(1L);
  }
}
