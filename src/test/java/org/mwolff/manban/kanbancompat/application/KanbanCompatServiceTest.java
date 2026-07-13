package org.mwolff.manban.kanbancompat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.board.application.BoardColumnRepository;
import org.mwolff.manban.board.application.BoardRepository;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.board.domain.BoardColumn;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.CardService.CardView;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.comment.application.CommentService;
import org.mwolff.manban.project.application.PermissionChecker;

/** Unit-Tests der Kanban-Compat-Schicht (Spaltennamen-Normalisierung + Verhalten an den Ports). */
class KanbanCompatServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long BOARD = 10L;

  private CardRepository cards;
  private BoardColumnRepository columns;
  private BoardRepository boards;
  private CardService cardService;
  private CommentService commentService;
  private PermissionChecker permissions;
  private KanbanCompatService service;

  private static KanbanPrincipal bound() {
    return new KanbanPrincipal(1L, 2L, 5L, BOARD);
  }

  private static List<BoardColumn> standardColumns() {
    return List.of(
        new BoardColumn(100L, BOARD, "Backlog", 0, null),
        new BoardColumn(101L, BOARD, "Ready", 1, null),
        new BoardColumn(102L, BOARD, "In Progress", 2, null),
        new BoardColumn(103L, BOARD, "In Review", 3, null),
        new BoardColumn(104L, BOARD, "Done", 4, null));
  }

  private static Card card(long id, long columnId, int number) {
    return new Card(
        id,
        BOARD,
        columnId,
        number,
        "T",
        "body",
        0,
        false,
        null,
        1L,
        FIXED,
        FIXED,
        CardType.CARD,
        null,
        null);
  }

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    columns = mock(BoardColumnRepository.class);
    boards = mock(BoardRepository.class);
    cardService = mock(CardService.class);
    commentService = mock(CommentService.class);
    permissions = mock(PermissionChecker.class);
    service =
        new KanbanCompatService(cards, columns, boards, cardService, commentService, permissions);
  }

  @ParameterizedTest
  @CsvSource({
    "Backlog, BACKLOG",
    "Ready, READY",
    "In Progress, IN_PROGRESS",
    "In Review, IN_REVIEW",
    "Done, DONE",
    "'  done  ', DONE",
    "In-Progress, IN_PROGRESS"
  })
  void canonicalKey_mapsKnownColumnName_toKey(String columnName, String expectedKey) {
    // When / Then: bekannter Spaltenname liefert den Kanban-Key
    assertThat(KanbanCompatService.canonicalKey(columnName)).contains(expectedKey);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "Unbekannt", "Todo", "123"})
  void canonicalKey_returnsEmpty_whenNoMatch(String columnName) {
    // When / Then: kein Treffer -> leeres Optional (nicht null)
    assertThat(KanbanCompatService.canonicalKey(columnName)).isEmpty();
  }

  @Test
  void items_groupsCardsByKanbanColumn() {
    // Given
    when(boards.findById(BOARD)).thenReturn(Optional.of(new Board(BOARD, 5L, "B", FIXED)));
    when(columns.findByBoardId(BOARD)).thenReturn(standardColumns());
    when(cards.findByBoardId(BOARD)).thenReturn(List.of(card(1L, 100L, 1)));

    // When
    Map<String, List<KanbanCompatService.Item>> grouped = service.items(bound());

    // Then
    assertThat(grouped.get("BACKLOG"))
        .extracting(KanbanCompatService.Item::number)
        .containsExactly(1);
  }

  @Test
  void items_skipsArchivedCards() {
    // Given
    Card archived =
        new Card(
            2L,
            BOARD,
            100L,
            2,
            "A",
            null,
            0,
            true,
            null,
            1L,
            FIXED,
            FIXED,
            CardType.CARD,
            null,
            null);
    when(boards.findById(BOARD)).thenReturn(Optional.of(new Board(BOARD, 5L, "B", FIXED)));
    when(columns.findByBoardId(BOARD)).thenReturn(standardColumns());
    when(cards.findByBoardId(BOARD)).thenReturn(List.of(archived));

    // When
    Map<String, List<KanbanCompatService.Item>> grouped = service.items(bound());

    // Then
    assertThat(grouped.get("BACKLOG")).isEmpty();
  }

  @Test
  void items_marksEpicItemsAsEpicType() {
    // Given: ein Epic auf dem Board
    Card epic =
        new Card(
            3L,
            BOARD,
            100L,
            3,
            "E",
            "body",
            0,
            false,
            null,
            1L,
            FIXED,
            FIXED,
            CardType.EPIC,
            null,
            "E");
    when(boards.findById(BOARD)).thenReturn(Optional.of(new Board(BOARD, 5L, "B", FIXED)));
    when(columns.findByBoardId(BOARD)).thenReturn(standardColumns());
    when(cards.findByBoardId(BOARD)).thenReturn(List.of(epic));

    // When
    Map<String, List<KanbanCompatService.Item>> grouped = service.items(bound());

    // Then
    assertThat(grouped.get("BACKLOG"))
        .singleElement()
        .extracting(KanbanCompatService.Item::type)
        .isEqualTo("epic");
  }

  @Test
  void items_marksRegularCardsAsCardType() {
    // Given: eine gewöhnliche Karte (kein Epic)
    when(boards.findById(BOARD)).thenReturn(Optional.of(new Board(BOARD, 5L, "B", FIXED)));
    when(columns.findByBoardId(BOARD)).thenReturn(standardColumns());
    when(cards.findByBoardId(BOARD)).thenReturn(List.of(card(1L, 100L, 1)));

    // When
    Map<String, List<KanbanCompatService.Item>> grouped = service.items(bound());

    // Then: der Typ ist „card" (nicht „epic")
    assertThat(grouped.get("BACKLOG"))
        .singleElement()
        .extracting(KanbanCompatService.Item::type)
        .isEqualTo("card");
  }

  @Test
  void items_saturatesFallbackKeyAtLastColumn_whenBoardHasMoreColumnsThanKeys() {
    // Given: sechs Spalten ohne kanonische Namen -> für jede greift der Positions-Fallback.
    // Ab der sechsten (Index 5) muss der Fallback-Index bei der letzten Kanban-Spalte (DONE)
    // gedeckelt werden (Math.min(i, size-1)); ein „size+1" (Mutant) liefe aus dem Index.
    List<BoardColumn> sixColumns =
        List.of(
            new BoardColumn(100L, BOARD, "Alpha", 0, null),
            new BoardColumn(101L, BOARD, "Beta", 1, null),
            new BoardColumn(102L, BOARD, "Gamma", 2, null),
            new BoardColumn(103L, BOARD, "Delta", 3, null),
            new BoardColumn(104L, BOARD, "Epsilon", 4, null),
            new BoardColumn(105L, BOARD, "Zeta", 5, null));
    when(boards.findById(BOARD)).thenReturn(Optional.of(new Board(BOARD, 5L, "B", FIXED)));
    when(columns.findByBoardId(BOARD)).thenReturn(sixColumns);
    when(cards.findByBoardId(BOARD)).thenReturn(List.of(card(1L, 105L, 1)));

    // When
    Map<String, List<KanbanCompatService.Item>> grouped = service.items(bound());

    // Then: die Karte in der sechsten Spalte landet unter dem gedeckelten Key „DONE"
    assertThat(grouped.get("DONE")).extracting(KanbanCompatService.Item::number).containsExactly(1);
  }

  @Test
  void items_throwsTokenNotBound_whenPrincipalUnbound() {
    // Given
    KanbanPrincipal unbound = new KanbanPrincipal(1L, 2L, null, null);

    // When / Then
    assertThatThrownBy(() -> service.items(unbound)).isInstanceOf(TokenNotBoundException.class);
  }

  @Test
  void items_throwsTokenNotBound_whenPrincipalNull() {
    // When / Then
    assertThatThrownBy(() -> service.items(null)).isInstanceOf(TokenNotBoundException.class);
  }

  @Test
  void create_delegatesToCardServiceInBacklog_byDefault() {
    // Given
    when(columns.findByBoardId(BOARD)).thenReturn(standardColumns());
    when(cardService.create(
            eq(1L), eq(BOARD), eq(100L), eq("Titel"), eq("Body"), isNull(), isNull()))
        .thenReturn(
            new CardView(
                1L,
                BOARD,
                100L,
                7,
                "Titel",
                "Body",
                0,
                false,
                null,
                List.of(),
                CardType.CARD,
                null,
                null));

    // When
    KanbanCompatService.Created created = service.create(bound(), "Titel", "Body", null);

    // Then
    assertThat(created.number()).isEqualTo(7);
  }

  @Test
  void create_defaultsToBacklog_whenColumnBlank() {
    // Given: leerer (blank) Spalten-Parameter -> BACKLOG
    when(columns.findByBoardId(BOARD)).thenReturn(standardColumns());
    when(cardService.create(
            eq(1L), eq(BOARD), eq(100L), eq("Titel"), eq("Body"), isNull(), isNull()))
        .thenReturn(
            new CardView(
                1L,
                BOARD,
                100L,
                7,
                "Titel",
                "Body",
                0,
                false,
                null,
                List.of(),
                CardType.CARD,
                null,
                null));

    // When
    KanbanCompatService.Created created = service.create(bound(), "Titel", "Body", "   ");

    // Then
    assertThat(created.number()).isEqualTo(7);
  }

  @Test
  void create_throwsInvalidKanbanColumn_whenColumnKeyUnknown() {
    // Given
    when(columns.findByBoardId(BOARD)).thenReturn(standardColumns());

    // When / Then: die Meldung muss aus dem COLUMNS-Guard stammen (nicht aus dem späteren
    // Board-Lookup), sonst bliebe ein Umgehen des Guards unentdeckt.
    KanbanPrincipal principal = bound();
    assertThatThrownBy(() -> service.create(principal, "Titel", "Body", "NOPE"))
        .isInstanceOf(InvalidKanbanColumnException.class)
        .hasMessageContaining("Unbekannte Kanban-Spalte");
  }

  @Test
  void create_throwsInvalidKanbanColumn_whenBoardHasNoColumnForKey() {
    // Given: gültiger Kanban-Key, aber das Board hat keine passende Spalte
    when(columns.findByBoardId(BOARD))
        .thenReturn(List.of(new BoardColumn(100L, BOARD, "Backlog", 0, null)));

    // When / Then
    KanbanPrincipal principal = bound();
    assertThatThrownBy(() -> service.create(principal, "Titel", "Body", "DONE"))
        .isInstanceOf(InvalidKanbanColumnException.class);
  }

  @Test
  void move_delegatesToCardService() {
    // Given
    when(cards.findById(1L)).thenReturn(Optional.of(card(1L, 100L, 1)));
    when(columns.findByBoardId(BOARD)).thenReturn(standardColumns());

    // When
    service.move(bound(), 1L, "DONE", 2);

    // Then
    verify(cardService).move(1L, 1L, 104L, 2);
  }

  @Test
  void move_throwsCardNotFound_whenCardNotOnBoard() {
    // Given
    Card otherBoard =
        new Card(
            1L,
            99L,
            100L,
            1,
            "T",
            null,
            0,
            false,
            null,
            1L,
            FIXED,
            FIXED,
            CardType.CARD,
            null,
            null);
    when(cards.findById(1L)).thenReturn(Optional.of(otherBoard));

    // When / Then
    assertThatThrownBy(() -> service.move(bound(), 1L, "DONE", 0))
        .isInstanceOf(org.mwolff.manban.card.application.CardNotFoundException.class);
  }

  @Test
  void move_throwsInvalidKanbanColumn_whenColumnNull() {
    // Given: Karte liegt auf dem Board, aber die Ziel-Spalte ist null
    when(cards.findById(1L)).thenReturn(Optional.of(card(1L, 100L, 1)));

    // When / Then
    KanbanPrincipal principal = bound();
    assertThatThrownBy(() -> service.move(principal, 1L, null, 0))
        .isInstanceOf(InvalidKanbanColumnException.class);
  }

  @Test
  void comment_delegatesToCommentService() {
    // Given
    when(cards.findById(1L)).thenReturn(Optional.of(card(1L, 100L, 1)));

    // When
    service.comment(bound(), 1L, "Hallo");

    // Then
    verify(commentService).create(1L, 1L, "Hallo");
  }

  @Test
  void comment_throwsCardNotFound_whenCardNotOnBoard() {
    // Given: die Karte liegt auf einem anderen Board — der Board-Guard muss greifen. Fällt der
    // requireCardOnBoard-Aufruf weg (Mutant), würde der Kommentar fälschlich angelegt.
    Card otherBoard =
        new Card(
            1L,
            99L,
            100L,
            1,
            "T",
            null,
            0,
            false,
            null,
            1L,
            FIXED,
            FIXED,
            CardType.CARD,
            null,
            null);
    when(cards.findById(1L)).thenReturn(Optional.of(otherBoard));

    // When / Then
    assertThatThrownBy(() -> service.comment(bound(), 1L, "Hallo"))
        .isInstanceOf(org.mwolff.manban.card.application.CardNotFoundException.class);
  }

  @Test
  void epics_mapsProgressFromCardService() {
    // Given
    when(cardService.listEpics(1L, BOARD))
        .thenReturn(List.of(new CardService.EpicView(5L, 3, "Epic", "desc", "E", 2, 4)));

    // When
    List<KanbanCompatService.Epic> result = service.epics(bound());

    // Then
    assertThat(result)
        .singleElement()
        .extracting(e -> e.progress().total(), e -> e.progress().done())
        .containsExactly(4, 2);
  }
}
