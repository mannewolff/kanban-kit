package org.mwolff.manban.kanbancompat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.CardService.BoardItemView;
import org.mwolff.manban.card.application.CardService.CardView;
import org.mwolff.manban.card.application.LabelService;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.comment.application.CommentService;
import org.mwolff.manban.comment.application.CommentService.CommentView;

/** Unit-Tests der Kanban-Compat-Schicht (Spaltennamen-Normalisierung + Verhalten an den Ports). */
// PMD.TooManyMethods: methodenreiche Testsuite — viele kleine @Test-Methoden je Erfolgs- und
// Fehlerpfad sind hier gewollt, kein Refactoring-Signal.
@SuppressWarnings("PMD.TooManyMethods")
class KanbanCompatServiceTest {

  private static final long BOARD = 10L;
  private static final String BACKLOG_KEY = "BACKLOG";

  private BoardService boardService;
  private CardService cardService;
  private LabelService labelService;
  private CommentService commentService;
  private KanbanCompatService service;

  private static KanbanPrincipal bound() {
    return new KanbanPrincipal(1L, 2L, 5L, BOARD, "Token");
  }

  private static List<ColumnView> standardColumns() {
    return List.of(
        new ColumnView(100L, "Backlog", 0, null),
        new ColumnView(101L, "Ready", 1, null),
        new ColumnView(102L, "In Progress", 2, null),
        new ColumnView(103L, "In Review", 3, null),
        new ColumnView(104L, "Done", 4, null));
  }

  private static BoardItemView item(long id, long columnId, int number) {
    return new BoardItemView(id, number, "T", "body", columnId, 0, false, null);
  }

  private static CardView pooledIdea(long id) {
    return new CardView(
        id,
        null,
        null,
        7,
        "Titel",
        "Body",
        0,
        false,
        true,
        null,
        List.of(),
        CardType.CARD,
        null,
        null,
        List.of(),
        null,
        List.of(),
        BOARD);
  }

  @BeforeEach
  void setUp() {
    boardService = mock(BoardService.class);
    cardService = mock(CardService.class);
    labelService = mock(LabelService.class);
    commentService = mock(CommentService.class);
    service = new KanbanCompatService(boardService, cardService, labelService, commentService);
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
    when(boardService.listColumns(BOARD)).thenReturn(standardColumns());
    when(cardService.listBoardItems(1L, BOARD)).thenReturn(List.of(item(1L, 100L, 1)));

    // When
    Map<String, List<KanbanCompatService.Item>> grouped = service.items(bound());

    // Then
    assertThat(grouped.get("BACKLOG"))
        .extracting(KanbanCompatService.Item::number)
        .containsExactly(1);
  }

  @Test
  void items_marksEpicItemsAsEpicType() {
    // Given: ein Epic auf dem Board
    when(boardService.listColumns(BOARD)).thenReturn(standardColumns());
    when(cardService.listBoardItems(1L, BOARD))
        .thenReturn(List.of(new BoardItemView(3L, 3, "E", "body", 100L, 0, true, null)));

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
    when(boardService.listColumns(BOARD)).thenReturn(standardColumns());
    when(cardService.listBoardItems(1L, BOARD)).thenReturn(List.of(item(1L, 100L, 1)));

    // When
    Map<String, List<KanbanCompatService.Item>> grouped = service.items(bound());

    // Then: der Typ ist „card" (nicht „epic")
    assertThat(grouped.get("BACKLOG"))
        .singleElement()
        .extracting(KanbanCompatService.Item::type)
        .isEqualTo("card");
  }

  @Test
  void items_delegatesVisibilityFilterToCardFacade() {
    // Der Ausschluss archivierter und im Ideen-Speicher liegender Karten (#434) liegt seit #458 im
    // card-Modul; die Compat-Schicht reicht die bereits gefilterte Liste unveraendert durch. Fiele
    // der Aufruf auf eine andere Quelle zurueck, waere hier nichts zu sehen.
    when(boardService.listColumns(BOARD)).thenReturn(standardColumns());
    when(cardService.listBoardItems(1L, BOARD)).thenReturn(List.of());

    Map<String, List<KanbanCompatService.Item>> grouped = service.items(bound());

    verify(cardService).listBoardItems(1L, BOARD);
    assertThat(grouped.get("BACKLOG")).isEmpty();
  }

  @Test
  void items_saturatesFallbackKeyAtLastColumn_whenBoardHasMoreColumnsThanKeys() {
    // Given: sechs Spalten ohne kanonische Namen -> für jede greift der Positions-Fallback.
    // Ab der sechsten (Index 5) muss der Fallback-Index bei der letzten Kanban-Spalte (DONE)
    // gedeckelt werden (Math.min(i, size-1)); ein „size+1" (Mutant) liefe aus dem Index.
    List<ColumnView> sixColumns =
        List.of(
            new ColumnView(100L, "Alpha", 0, null),
            new ColumnView(101L, "Beta", 1, null),
            new ColumnView(102L, "Gamma", 2, null),
            new ColumnView(103L, "Delta", 3, null),
            new ColumnView(104L, "Epsilon", 4, null),
            new ColumnView(105L, "Zeta", 5, null));
    when(boardService.listColumns(BOARD)).thenReturn(sixColumns);
    when(cardService.listBoardItems(1L, BOARD)).thenReturn(List.of(item(1L, 105L, 1)));

    // When
    Map<String, List<KanbanCompatService.Item>> grouped = service.items(bound());

    // Then: die Karte in der sechsten Spalte landet unter dem gedeckelten Key „DONE"
    assertThat(grouped.get("DONE")).extracting(KanbanCompatService.Item::number).containsExactly(1);
  }

  @Test
  void items_throwsTokenNotBound_whenPrincipalUnbound() {
    // Given
    KanbanPrincipal unbound = new KanbanPrincipal(1L, 2L, null, null, "Token");

    // When / Then
    assertThatThrownBy(() -> service.items(unbound)).isInstanceOf(TokenNotBoundException.class);
  }

  @Test
  void items_throwsTokenNotBound_whenPrincipalNull() {
    // When / Then
    assertThatThrownBy(() -> service.items(null)).isInstanceOf(TokenNotBoundException.class);
  }

  @Test
  void create_delegatesToCreateProjectIdea_asPoolIdea() {
    // Given: der board-gebundene Token liefert das Board; daraus wird das Projekt abgeleitet und
    // das Board nur noch als Zielboard (target_board_id) notiert.
    when(boardService.requireProjectId(BOARD)).thenReturn(5L);
    when(cardService.createProjectIdea(1L, 5L, "Titel", "Body", BOARD, null, null))
        .thenReturn(new CardService.IdeaCreation(pooledIdea(42L), true));

    // When
    KanbanCompatService.Created created =
        service.create(bound(), "Titel", "Body", null, false, null, false, null, null);

    // Then: kein board-gebundenes create mehr, sondern eine board-lose Pool-Idee; zurück kommt
    // deren id samt der projektweiten Nummer (#402), damit der Adapter sofort #N zeigen kann.
    verify(cardService).createProjectIdea(1L, 5L, "Titel", "Body", BOARD, null, null);
    assertThat(created.id()).isEqualTo(42L);
    assertThat(created.number()).isEqualTo(7);
  }

  @Test
  void create_ignoresColumnAndIdeaStored() {
    // Given: seit Entscheidung B sind column und ideaStored gegenstandslos — egal was reinkommt,
    // es entsteht dieselbe Pool-Idee (keine Spalten-Validierung, kein Ideen-Flag-Durchreichen).
    when(boardService.requireProjectId(BOARD)).thenReturn(5L);
    when(cardService.createProjectIdea(1L, 5L, "Titel", "Body", BOARD, null, null))
        .thenReturn(new CardService.IdeaCreation(pooledIdea(42L), true));

    // When: absichtlich eine (frueher unbekannte) Spalte + ideaStored=true
    KanbanCompatService.Created created =
        service.create(
            bound(), "Titel", "Body", "VOELLIG-UNBEKANNT", true, null, false, null, null);

    // Then: keine InvalidKanbanColumnException, Delegation unveraendert
    verify(cardService).createProjectIdea(1L, 5L, "Titel", "Body", BOARD, null, null);
    assertThat(created.id()).isEqualTo(42L);
    assertThat(created.number()).isEqualTo(7);
  }

  @Test
  void create_normalizesExternalKey_andReportsCreatedFlag() {
    // Given: Schlüssel mit Rand-Whitespace; der Service traf ein Duplikat (created=false).
    when(boardService.requireProjectId(BOARD)).thenReturn(5L);
    when(cardService.createProjectIdea(1L, 5L, "Titel", "Body", BOARD, "sonar:abc", null))
        .thenReturn(new CardService.IdeaCreation(pooledIdea(42L), false));

    // When
    KanbanCompatService.Created created =
        service.create(bound(), "Titel", "Body", null, false, "  sonar:abc  ", false, null, null);

    // Then: getrimmt durchgereicht, Duplikat als created=false gemeldet.
    verify(cardService).createProjectIdea(1L, 5L, "Titel", "Body", BOARD, "sonar:abc", null);
    assertThat(created.created()).isFalse();
    assertThat(created.id()).isEqualTo(42L);
  }

  @Test
  void create_capsExternalKeyAt100Chars_andTreatsBlankAsMissing() {
    // Given
    when(boardService.requireProjectId(BOARD)).thenReturn(5L);
    when(cardService.createProjectIdea(anyLong(), anyLong(), any(), any(), any(), any(), any()))
        .thenReturn(new CardService.IdeaCreation(pooledIdea(42L), true));

    // When: überlanger Schlüssel und blanker Schlüssel
    service.create(bound(), "Titel", "Body", null, false, "x".repeat(150), false, null, null);
    service.create(bound(), "Titel", "Body", null, false, "   ", false, null, null);

    // Then: gekappt auf 100 bzw. null (kein Schlüssel)
    verify(cardService).createProjectIdea(1L, 5L, "Titel", "Body", BOARD, "x".repeat(100), null);
    verify(cardService).createProjectIdea(1L, 5L, "Titel", "Body", BOARD, null, null);
  }

  @Test
  void replaceDependencies_delegatesWithProjectOfBoundBoard() {
    // #566: Der Guard ist projekt- und nicht boardbezogen, damit auch board-lose Pool-Ideen
    // erreichbar sind. Die Projekt-ID stammt aus dem gebundenen Board und wird durchgereicht.
    when(boardService.requireProjectId(BOARD)).thenReturn(5L);

    service.replaceDependencies(bound(), 42L, List.of(7, 8));

    verify(cardService).replaceDependenciesFromIngest(1L, 42L, 5L, List.of(7, 8));
  }

  @Test
  void replaceDependencies_passesNullThrough() {
    // null loescht die Verweise — der Service reicht es unveraendert weiter statt es zu ersetzen.
    when(boardService.requireProjectId(BOARD)).thenReturn(5L);

    service.replaceDependencies(bound(), 42L, null);

    verify(cardService).replaceDependenciesFromIngest(1L, 42L, 5L, null);
  }

  @Test
  void replaceDependencies_requiresBoundToken() {
    // Ohne Board-Bindung gibt es kein Projekt — der Aufruf darf das card-Modul nicht erreichen.
    assertThatThrownBy(
            () ->
                service.replaceDependencies(
                    new KanbanPrincipal(1L, 2L, 5L, null, "Token"), 42L, List.of(7)))
        .isInstanceOf(TokenNotBoundException.class);
    verify(cardService, org.mockito.Mockito.never())
        .replaceDependenciesFromIngest(anyLong(), anyLong(), anyLong(), any());
  }

  @Test
  void create_withDirectAndColumn_resolvesTheRequestedColumn() {
    // #569: Der direct-Zweig respektiert die angeforderte Spalte, statt immer die erste zu nehmen.
    when(boardService.requireProjectId(BOARD)).thenReturn(5L);
    when(boardService.listColumns(BOARD)).thenReturn(standardColumns());
    when(cardService.createDirect(1L, BOARD, 101L, "Titel", "Body", null, null, null))
        .thenReturn(new CardService.IdeaCreation(pooledIdea(42L), true));

    service.create(bound(), "Titel", "Body", "READY", false, null, true, null, null);

    verify(cardService).createDirect(1L, BOARD, 101L, "Titel", "Body", null, null, null);
    verify(boardService, org.mockito.Mockito.never()).firstColumn(anyLong());
  }

  @Test
  void create_withDirectAndNullColumn_usesFirstColumn() {
    // Fehlendes column bleibt beim heutigen Verhalten — der Sonar-Sync sendet keines.
    when(boardService.requireProjectId(BOARD)).thenReturn(5L);
    when(boardService.firstColumn(BOARD)).thenReturn(new ColumnView(100L, "Backlog", 0, null));
    when(cardService.createDirect(1L, BOARD, 100L, "Titel", "Body", null, null, null))
        .thenReturn(new CardService.IdeaCreation(pooledIdea(42L), true));

    service.create(bound(), "Titel", "Body", null, false, null, true, null, null);

    verify(cardService).createDirect(1L, BOARD, 100L, "Titel", "Body", null, null, null);
  }

  @Test
  void create_withDirectAndBlankColumn_isRejected() {
    // Ein leerer String ist ein angegebener, ungueltiger Key — nicht dasselbe wie „fehlt".
    when(boardService.requireProjectId(BOARD)).thenReturn(5L);

    assertThatThrownBy(
            () -> service.create(bound(), "Titel", "Body", "   ", false, null, true, null, null))
        .isInstanceOf(InvalidKanbanColumnException.class);
    verify(cardService, org.mockito.Mockito.never())
        .createDirect(anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any());
  }

  @Test
  void create_withDirectAndUnknownColumnKey_isRejected() {
    when(boardService.requireProjectId(BOARD)).thenReturn(5L);

    assertThatThrownBy(
            () -> service.create(bound(), "Titel", "Body", "FOO", false, null, true, null, null))
        .isInstanceOf(InvalidKanbanColumnException.class);
    verify(cardService, org.mockito.Mockito.never())
        .createDirect(anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any());
  }

  @Test
  void create_withDirectAndDoneColumn_isRejected() {
    // Anlegen als „erledigt" umgeht Zykluszeit und Done-Retention: doCreate setzt movedToDoneAt
    // nicht, und DoneRetentionService archiviert allein darueber. Die Karte laege dauerhaft fest.
    //
    // Das Board hat hier ausdruecklich eine Done-Spalte. Ohne sie wuerde schon columnIdForKey
    // dieselbe Exception werfen, und der Test koennte nicht unterscheiden, ob die Ablehnung von
    // dieser Regel kommt oder blosser Nebeneffekt eines Boards ohne Done ist.
    when(boardService.requireProjectId(BOARD)).thenReturn(5L);
    when(boardService.listColumns(BOARD)).thenReturn(standardColumns());

    assertThatThrownBy(
            () -> service.create(bound(), "Titel", "Body", "DONE", false, null, true, null, null))
        .isInstanceOf(InvalidKanbanColumnException.class);
    verify(cardService, org.mockito.Mockito.never())
        .createDirect(anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any());
  }

  @Test
  void create_withDirectAndLowercaseDone_isRejected() {
    // Die Ablehnung haengt am normalisierten Key, nicht an der Schreibweise des Aufrufers.
    when(boardService.requireProjectId(BOARD)).thenReturn(5L);
    when(boardService.listColumns(BOARD)).thenReturn(standardColumns());

    assertThatThrownBy(
            () -> service.create(bound(), "Titel", "Body", " done ", false, null, true, null, null))
        .isInstanceOf(InvalidKanbanColumnException.class);
  }

  @Test
  void create_withoutDirect_ignoresColumnEntirely() {
    // Ohne direct bleibt column gegenstandslos — auch ein DONE fuehrt nicht zur Ablehnung.
    when(boardService.requireProjectId(BOARD)).thenReturn(5L);
    when(cardService.createProjectIdea(1L, 5L, "Titel", "Body", BOARD, null, null))
        .thenReturn(new CardService.IdeaCreation(pooledIdea(42L), true));

    KanbanCompatService.Created created =
        service.create(bound(), "Titel", "Body", "DONE", false, null, false, null, null);

    assertThat(created.id()).isEqualTo(42L);
  }

  @Test
  void create_withDirectAndIdeaStored_behavesLikeDirectAlone() {
    // Die konflikttraechtige Kombination: direct gewinnt, ideaStored bleibt wirkungslos.
    when(boardService.requireProjectId(BOARD)).thenReturn(5L);
    when(boardService.listColumns(BOARD)).thenReturn(standardColumns());
    when(cardService.createDirect(1L, BOARD, 101L, "Titel", "Body", null, null, null))
        .thenReturn(new CardService.IdeaCreation(pooledIdea(42L), true));

    service.create(bound(), "Titel", "Body", "READY", true, null, true, null, null);

    verify(cardService).createDirect(1L, BOARD, 101L, "Titel", "Body", null, null, null);
    verify(cardService, org.mockito.Mockito.never())
        .createProjectIdea(anyLong(), anyLong(), any(), any(), any(), any(), any());
  }

  @Test
  void create_withNumber_passesItThroughToDirectPath() {
    // #565: Die vorgegebene Nummer erreicht den Anlage-Pfad unveraendert.
    when(boardService.requireProjectId(BOARD)).thenReturn(5L);
    when(boardService.firstColumn(BOARD)).thenReturn(new ColumnView(100L, "Backlog", 0, null));
    when(cardService.createDirect(1L, BOARD, 100L, "Titel", "Body", "github#278", 278, null))
        .thenReturn(new CardService.IdeaCreation(pooledIdea(42L), true));

    service.create(bound(), "Titel", "Body", null, false, "github#278", true, 278, null);

    verify(cardService).createDirect(1L, BOARD, 100L, "Titel", "Body", "github#278", 278, null);
  }

  @Test
  void create_withNumberWithoutDirect_isRejected() {
    // Der Ideen-Pool ist fuer ungesichtete Rohanforderungen — eine migrierte Karte hat ihren
    // Platz bereits. Requestfehler, kein Zustandskonflikt.
    when(boardService.requireProjectId(BOARD)).thenReturn(5L);

    assertThatThrownBy(
            () ->
                service.create(
                    bound(), "Titel", "Body", null, false, "github#278", false, 278, null))
        .isInstanceOf(InvalidNumberedIngestException.class);
    verify(cardService, org.mockito.Mockito.never())
        .createProjectIdea(anyLong(), anyLong(), any(), any(), any(), any(), any());
  }

  @Test
  void create_withNumberWithoutExternalKey_isRejected() {
    // Ohne Schluessel legte der erste Aufruf eine schluessellose Karte an — und die
    // Import-Vorbedingung lehnte ab dem zweiten Aufruf denselben Import ab.
    when(boardService.requireProjectId(BOARD)).thenReturn(5L);

    assertThatThrownBy(
            () -> service.create(bound(), "Titel", "Body", null, false, null, true, 278, null))
        .isInstanceOf(InvalidNumberedIngestException.class);
  }

  @Test
  void create_withNumberAndBlankExternalKey_isRejected() {
    // Ein leerer Schluessel normalisiert zu null und zaehlt damit als fehlend.
    when(boardService.requireProjectId(BOARD)).thenReturn(5L);

    assertThatThrownBy(
            () -> service.create(bound(), "Titel", "Body", null, false, "   ", true, 278, null))
        .isInstanceOf(InvalidNumberedIngestException.class);
  }

  @Test
  void create_withoutNumber_skipsImportPreconditions() {
    // Ohne Nummer bleibt der Pfad unveraendert: weder direct noch externalKey sind Pflicht.
    when(boardService.requireProjectId(BOARD)).thenReturn(5L);
    when(cardService.createProjectIdea(1L, 5L, "Titel", "Body", BOARD, null, null))
        .thenReturn(new CardService.IdeaCreation(pooledIdea(42L), true));

    KanbanCompatService.Created created =
        service.create(bound(), "Titel", "Body", null, false, null, false, null, null);

    assertThat(created.id()).isEqualTo(42L);
  }

  @Test
  void create_withDirect_routesToFirstColumnOfTokenBoard() {
    // Given (#535): direct=true umgeht den Pool und legt in der ersten Spalte des Token-Boards an.
    when(boardService.requireProjectId(BOARD)).thenReturn(5L);
    when(boardService.firstColumn(BOARD)).thenReturn(new ColumnView(100L, "Backlog", 0, null));
    when(cardService.createDirect(1L, BOARD, 100L, "Titel", "Body", "sonar:abc", null, null))
        .thenReturn(new CardService.IdeaCreation(pooledIdea(42L), true));

    // When
    KanbanCompatService.Created created =
        service.create(bound(), "Titel", "Body", null, false, "sonar:abc", true, null, null);

    // Then: Board-Pfad statt Pool-Pfad, created durchgereicht.
    verify(cardService).createDirect(1L, BOARD, 100L, "Titel", "Body", "sonar:abc", null, null);
    verify(cardService, org.mockito.Mockito.never())
        .createProjectIdea(anyLong(), anyLong(), any(), any(), any(), any(), any());
    assertThat(created.id()).isEqualTo(42L);
    assertThat(created.created()).isTrue();
  }

  @Test
  void create_throwsBoardNotFound_whenBoardMissing() {
    // Given: der Token ist gebunden, aber das Board existiert nicht (mehr)
    when(boardService.requireProjectId(BOARD)).thenThrow(new BoardNotFoundException());

    // When / Then
    KanbanPrincipal principal = bound();
    assertThatThrownBy(
            () -> service.create(principal, "Titel", "Body", null, false, null, false, null, null))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void move_throwsInvalidKanbanColumn_whenBoardHasNoColumnForKey() {
    // Given: gültiger Kanban-Key, aber das Board hat keine passende Spalte. Dieser
    // columnIdForKey-Guard ist seit Entscheidung B nur noch über move erreichbar (create wertet
    // keine Spalte mehr aus).
    when(boardService.listColumns(BOARD))
        .thenReturn(List.of(new ColumnView(100L, "Backlog", 0, null)));

    // When / Then
    KanbanPrincipal principal = bound();
    assertThatThrownBy(() -> service.move(principal, 1L, "DONE", 0))
        .isInstanceOf(InvalidKanbanColumnException.class);
  }

  @Test
  void move_delegatesToCardService() {
    // Given
    when(boardService.listColumns(BOARD)).thenReturn(standardColumns());

    // When
    service.move(bound(), 1L, "DONE", 2);

    // Then
    verify(cardService).move(1L, 1L, 104L, 2);
  }

  @Test
  void update_writesContentAndReturnsItemInBoardForm() {
    // Given: die Karte liegt auf dem Board; die Fassade meldet den neuen Stand zurueck.
    when(boardService.listColumns(BOARD)).thenReturn(standardColumns());
    when(cardService.updateContent(1L, 7L, "Neuer Titel", "Neuer Rumpf"))
        .thenReturn(
            new BoardItemView(7L, 42, "Neuer Titel", "Neuer Rumpf", 102L, 3, false, "github#7"));
    when(labelService.namesByCard(BOARD, List.of(7L))).thenReturn(Map.of(7L, List.of("bug")));

    // When
    KanbanCompatService.Item updated = service.update(bound(), 7L, "Neuer Titel", "Neuer Rumpf");

    // Then: Item traegt den neuen Inhalt und die Board-Einordnung der Karte.
    assertThat(updated)
        .extracting(
            KanbanCompatService.Item::id,
            KanbanCompatService.Item::number,
            KanbanCompatService.Item::title,
            KanbanCompatService.Item::body,
            KanbanCompatService.Item::column,
            KanbanCompatService.Item::position,
            KanbanCompatService.Item::type,
            KanbanCompatService.Item::labels)
        .containsExactly(
            7L, 42, "Neuer Titel", "Neuer Rumpf", "IN_PROGRESS", 3, "card", List.of("bug"));
  }

  @Test
  void update_marksEpicsAsEpic_andFallsBackToBacklog_whenColumnUnknown() {
    // Given: Epic ohne bekannte Spalte (columnId trifft keine Board-Spalte) und ohne Labels.
    when(boardService.listColumns(BOARD)).thenReturn(standardColumns());
    when(cardService.updateContent(1L, 8L, "Epic", null))
        .thenReturn(new BoardItemView(8L, 43, "Epic", null, 999L, 0, true, null));
    when(labelService.namesByCard(BOARD, List.of(8L))).thenReturn(Map.of());

    // When
    KanbanCompatService.Item updated = service.update(bound(), 8L, "Epic", null);

    // Then: type spiegelt das Epic, die unbekannte Spalte faellt auf BACKLOG zurueck.
    assertThat(updated.type()).isEqualTo("epic");
    assertThat(updated.column()).isEqualTo(BACKLOG_KEY);
    assertThat(updated.body()).isNull();
    assertThat(updated.labels()).isEmpty();
  }

  @Test
  void update_throwsCardNotFound_whenCardNotOnBoard() {
    // Given: der Board-Guard schlaegt an. Faellt der requireOnBoard-Aufruf weg (Mutant), wuerde
    // eine fremde oder im Ideen-Speicher liegende Karte ueberschrieben.
    doThrow(new CardNotFoundException()).when(cardService).requireOnBoard(9L, BOARD);

    // When / Then
    KanbanPrincipal principal = bound();
    assertThatThrownBy(() -> service.update(principal, 9L, "Neu", "Neu"))
        .isInstanceOf(CardNotFoundException.class);
    verify(cardService, never()).updateContent(anyLong(), anyLong(), any(), any());
  }

  @Test
  void move_throwsCardNotFound_whenCardNotOnBoard() {
    // Given: der Board-Guard der card-Fassade schlaegt an (fremdes Board oder Ideen-Speicher).
    // Fällt der requireOnBoard-Aufruf weg (Mutant), würde die Karte fälschlich verschoben.
    doThrow(new CardNotFoundException()).when(cardService).requireOnBoard(1L, BOARD);

    // When / Then
    assertThatThrownBy(() -> service.move(bound(), 1L, "DONE", 0))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void move_throwsInvalidKanbanColumn_whenColumnNull() {
    // Given: Karte liegt auf dem Board, aber die Ziel-Spalte ist null

    // When / Then: die Meldung muss aus dem COLUMNS-Guard stammen ("Unbekannte Kanban-Spalte"),
    // nicht aus dem späteren Board-Lookup — sonst bliebe ein Umgehen des Guards unentdeckt.
    KanbanPrincipal principal = bound();
    assertThatThrownBy(() -> service.move(principal, 1L, null, 0))
        .isInstanceOf(InvalidKanbanColumnException.class)
        .hasMessageContaining("Unbekannte Kanban-Spalte");
  }

  @Test
  void comment_delegatesToCommentService() {
    // When
    service.comment(bound(), 1L, "Hallo");

    // Then
    verify(commentService).create(1L, 1L, "Hallo");
  }

  @Test
  void comment_throwsCardNotFound_whenCardNotOnBoard() {
    // Given: der Board-Guard der card-Fassade schlaegt an. Fällt der requireOnBoard-Aufruf weg
    // (Mutant), würde der Kommentar fälschlich angelegt.
    doThrow(new CardNotFoundException()).when(cardService).requireOnBoard(1L, BOARD);

    // When / Then
    assertThatThrownBy(() -> service.comment(bound(), 1L, "Hallo"))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void addLabel_delegatesToLabelService() {
    // When
    service.addLabel(bound(), 1L, "kit:nightrun");

    // Then
    verify(labelService).addToCard(1L, 1L, "kit:nightrun");
  }

  @Test
  void addLabel_throwsCardNotFound_whenCardNotOnBoard() {
    // Given: der Board-Guard der card-Fassade schlaegt an. Fällt der requireOnBoard-Aufruf weg
    // (Mutant), bekäme eine Karte eines fremden Boards das Label.
    doThrow(new CardNotFoundException()).when(cardService).requireOnBoard(1L, BOARD);

    // When / Then
    assertThatThrownBy(() -> service.addLabel(bound(), 1L, "kit:nightrun"))
        .isInstanceOf(CardNotFoundException.class);
    verify(labelService, never()).addToCard(anyLong(), anyLong(), anyString());
  }

  @Test
  void addLabel_throwsTokenNotBound_whenPrincipalUnbound() {
    // Given
    KanbanPrincipal unbound = new KanbanPrincipal(1L, 2L, null, null, "Token");

    // When / Then
    assertThatThrownBy(() -> service.addLabel(unbound, 1L, "kit:nightrun"))
        .isInstanceOf(TokenNotBoundException.class);
  }

  @Test
  void removeLabel_delegatesToLabelService() {
    // When
    service.removeLabel(bound(), 1L, "kit:nightrun");

    // Then
    verify(labelService).removeFromCard(1L, 1L, "kit:nightrun");
  }

  @Test
  void removeLabel_throwsCardNotFound_whenCardNotOnBoard() {
    // Given: wie beim Hinzufuegen — ohne den Guard verlöre eine fremde Karte ihr Label.
    doThrow(new CardNotFoundException()).when(cardService).requireOnBoard(1L, BOARD);

    // When / Then
    assertThatThrownBy(() -> service.removeLabel(bound(), 1L, "kit:nightrun"))
        .isInstanceOf(CardNotFoundException.class);
    verify(labelService, never()).removeFromCard(anyLong(), anyLong(), anyString());
  }

  @Test
  void removeLabel_throwsTokenNotBound_whenPrincipalUnbound() {
    // Given
    KanbanPrincipal unbound = new KanbanPrincipal(1L, 2L, null, null, "Token");

    // When / Then
    assertThatThrownBy(() -> service.removeLabel(unbound, 1L, "kit:nightrun"))
        .isInstanceOf(TokenNotBoundException.class);
  }

  @Test
  void listComments_mapsAuthorBodyAndCreatedAt_inServiceOrder() {
    // Given: die Kommentar-Fassade liefert bereits chronologisch sortiert
    Instant first = Instant.parse("2026-01-01T10:00:00Z");
    Instant second = Instant.parse("2026-01-01T11:00:00Z");
    when(commentService.list(1L, 42L))
        .thenReturn(
            List.of(
                new CommentView(9L, 42L, 3L, "Anna", "Erster", first, first),
                new CommentView(10L, 42L, 4L, "Bert", "Zweiter", second, second)));

    // When
    List<KanbanCompatService.Comment> result = service.listComments(bound(), 42L);

    // Then: Reihenfolge und Feldabbildung bleiben erhalten
    assertThat(result)
        .extracting(
            KanbanCompatService.Comment::author,
            KanbanCompatService.Comment::body,
            KanbanCompatService.Comment::createdAt)
        .containsExactly(tuple("Anna", "Erster", first), tuple("Bert", "Zweiter", second));
  }

  @Test
  void listComments_returnsEmptyList_whenItemHasNoComments() {
    // Given: keine Kommentare am Item

    // When / Then: leere Liste statt null
    assertThat(service.listComments(bound(), 42L)).isEmpty();
  }

  @Test
  void listComments_throwsCardNotFound_whenCardNotOnBoard() {
    // Given: der Board-Guard der card-Fassade schlaegt an. Fällt der requireOnBoard-Aufruf weg
    // (Mutant), würden Kommentare fremder Karten lesbar.
    doThrow(new CardNotFoundException()).when(cardService).requireOnBoard(42L, BOARD);

    // When / Then
    KanbanPrincipal principal = bound();
    assertThatThrownBy(() -> service.listComments(principal, 42L))
        .isInstanceOf(CardNotFoundException.class);
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

  @Test
  void items_exposesLabelNamesPerCard() {
    // Given: die Label-Namen kommen als Batch aus der card-Fassade, abgefragt fuer genau die
    // sichtbaren Karten-IDs.
    when(boardService.listColumns(BOARD)).thenReturn(standardColumns());
    when(cardService.listBoardItems(1L, BOARD)).thenReturn(List.of(item(1L, 100L, 1)));
    when(labelService.namesByCard(BOARD, List.of(1L))).thenReturn(Map.of(1L, List.of("Bug", "Ux")));

    // When
    Map<String, List<KanbanCompatService.Item>> grouped = service.items(bound());

    // Then: pro Karte die Label-Namen (nicht IDs)
    assertThat(grouped.get("BACKLOG"))
        .singleElement()
        .extracting(KanbanCompatService.Item::labels)
        .isEqualTo(List.of("Bug", "Ux"));
  }

  @Test
  void items_returnsEmptyLabels_forCardMissingInLabelBatch() {
    // Given: die Batch-Antwort kennt die Karte nicht — die Ausgabe muss dennoch eine leere Liste
    // tragen (nicht null), damit der Adapter kein Sonderfall-Handling braucht.
    when(boardService.listColumns(BOARD)).thenReturn(standardColumns());
    when(cardService.listBoardItems(1L, BOARD)).thenReturn(List.of(item(1L, 100L, 1)));
    when(labelService.namesByCard(BOARD, List.of(1L))).thenReturn(Map.of());

    // When
    Map<String, List<KanbanCompatService.Item>> grouped = service.items(bound());

    // Then: leere Liste, nicht null
    assertThat(grouped.get("BACKLOG"))
        .singleElement()
        .extracting(KanbanCompatService.Item::labels)
        .isEqualTo(List.of());
  }
}
