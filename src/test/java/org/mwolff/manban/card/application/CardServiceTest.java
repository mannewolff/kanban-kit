package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.mwolff.manban.board.application.BoardColumnRepository;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardRepository;
import org.mwolff.manban.board.application.ColumnNotFoundException;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.board.domain.BoardColumn;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;

/** Verhaltenstests der Karten- und Epic-Use-Cases (Mockito an den Ports). */
// PMD.TooManyMethods: umfassende Unit-Suite (Karten + Epics, Erfolgs- und Fehlerpfade je
// Use-Case). Viele kleine @Test-Methoden sind hier gewollt, kein God-Class-Smell.
@SuppressWarnings("PMD.TooManyMethods")
class CardServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long BOARD = 10L;

  private CardRepository cards;
  private CardDependencyRepository dependencies;
  private BoardRepository boards;
  private BoardColumnRepository columns;
  private PermissionChecker permissions;
  private CardColumnTransitionRepository transitions;
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
        id, BOARD, columnId, number, "Titel", null, 0, archived, done, 1L, FIXED, FIXED, type,
        parentId, shortcode);
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
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    service =
        new CardService(cards, dependencies, boards, columns, permissions, transitions, clock);
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
        c.movedToDoneAt(),
        c.createdBy(),
        c.createdAt(),
        c.updatedAt(),
        c.type(),
        c.parentId(),
        c.shortcode());
  }

  // --- create -----------------------------------------------------------

  @Test
  void create_setsCreatedAtFromInjectedClock() {
    // Given
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));
    when(cards.maxNumberInBoard(BOARD)).thenReturn(0);
    when(cards.maxActivePositionInColumn(20L)).thenReturn(-1);

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.create(1L, BOARD, 20L, "Titel", null, null, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().createdAt()).isEqualTo(FIXED);
  }

  @Test
  void create_assignsNextBoardNumber() {
    // Given
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));
    when(cards.maxNumberInBoard(BOARD)).thenReturn(7);
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
    when(cards.maxNumberInBoard(BOARD)).thenReturn(0);
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
    when(cards.maxNumberInBoard(BOARD)).thenReturn(4);
    when(cards.findByBoardId(BOARD))
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
    when(cards.maxNumberInBoard(BOARD)).thenReturn(0);
    when(cards.findByBoardId(BOARD))
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
    when(cards.maxNumberInBoard(BOARD)).thenReturn(0);
    when(cards.findByBoardId(BOARD)).thenReturn(List.of());

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
    when(cards.maxNumberInBoard(BOARD)).thenReturn(0);

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
    when(cards.maxNumberInBoard(BOARD)).thenReturn(0);

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
    service.update(1L, 1L, "Neu", null, null, null, 30L);

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
    service.update(1L, 5L, "Neu", null, null, "NEW", null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().shortcode()).isEqualTo("NEW");
  }

  @Test
  void update_replacesDependencies_whenProvided() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(cards.findByBoardId(BOARD))
        .thenReturn(List.of(card(2L, 20L, 3, false, null, CardType.CARD, null, null)));

    // When
    service.update(1L, 1L, "Neu", null, List.of(3), null, null);

    // Then
    verify(dependencies).replaceDependencies(1L, List.of(3));
  }

  @Test
  void update_throwsCardNotFound_whenCardUnknown() {
    // Given
    when(cards.findById(1L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.update(1L, 1L, "Neu", null, null, null, null))
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

  @Test
  void delete_removesDependenciesAndCard() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    // When
    service.delete(1L, 1L);

    // Then
    verify(cards).deleteById(1L);
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
            null,
            1L,
            FIXED,
            FIXED,
            CardType.EPIC,
            null,
            "E");
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
    // Eine leere Liste wird ohne Board-Lookup direkt geleert (Kurzschluss des isEmpty-Zweigs).
    // Ein Umgehen dieses Zweigs (Mutant) würde die Board-Nummern unnötig nachladen.
    verify(cards, never()).findByBoardId(BOARD);
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
    service.update(1L, 1L, "Neu", null, null, null, null);

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
    when(cards.maxNumberInBoard(BOARD)).thenReturn(4);

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
    CardService.CardView view = service.update(1L, 1L, "Neu", null, null, null, null);

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
  void delete_removesCardDependencies() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    // When
    service.delete(1L, 1L);

    // Then
    verify(dependencies).deleteByCardId(1L);
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
    when(cards.maxNumberInBoard(20L)).thenReturn(7);
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
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().parentId()).isNull();
    assertThat(captor.getValue().movedToDoneAt()).isNull();
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

  // --- Zykluszeit-Tracking (card_column_transition) ---------------------

  @Test
  void create_opensColumnTransition() {
    // Given
    when(columns.findById(20L)).thenReturn(Optional.of(column(20L, "Backlog", 0)));
    when(cards.maxNumberInBoard(BOARD)).thenReturn(0);
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
}
