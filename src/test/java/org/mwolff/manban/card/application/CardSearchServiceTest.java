package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectNotFoundException;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.project.domain.Permission;

/**
 * Verhaltenstests der Nummernsuche (Mockito an den Ports). Die Testmethoden stammen unverändert aus
 * {@code CardServiceTest} (Issue #1391, Plan #1387 E6); {@link KartenSicht} entsteht echt aus
 * denselben Port-Mocks.
 */
class CardSearchServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;
  // Zweites Projekt/Board fuer die projektuebergreifende Nummernsuche (#489).
  private static final long PROJECT_B = 2L;
  private static final long BOARD_B = 11L;

  private CardRepository cards;
  private BoardService boardService;
  private PermissionChecker permissions;
  private ProjectService projects;
  private CardSearchService service;

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
        parentId, shortcode, null, PROJECT, null, null, null, null);
  }

  private static ColumnView column(long id, String name, int position) {
    return new ColumnView(id, name, position, null);
  }

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    boardService = mock(BoardService.class);
    permissions = mock(PermissionChecker.class);
    projects = mock(ProjectService.class);
    KartenSicht sicht =
        new KartenSicht(
            cards,
            new KartenAbhaengigkeiten(mock(CardDependencyRepository.class), cards),
            new KartenZuordnung(
                mock(CardAssigneeRepository.class),
                mock(LabelRepository.class),
                mock(CardLabelRepository.class),
                permissions),
            permissions);
    service = new CardSearchService(cards, boardService, permissions, projects, sicht);
  }

  // --- getByNumber (#408) -----------------------------------------------

  @Test
  void getByNumber_returnsBoardCardView_forMember() {
    when(cards.findByProjectIdAndNumber(PROJECT, 42))
        .thenReturn(Optional.of(card(1L, 20L, 42, false, null, CardType.CARD, null, null)));

    CardView view = service.getByNumber(5L, PROJECT, 42);

    assertThat(view.id()).isEqualTo(1L);
    assertThat(view.number()).isEqualTo(42);
    assertThat(view.boardId()).isEqualTo(BOARD);
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

  // --- findOnBoardByNumber: Einzelabruf der Kanban-Strecke (Plan #1574, A3) ---

  /** Eine Karte mit Titel, Typ, Archiv-Kennzeichen, Status und Herkunft auf {@code boardId}. */
  private static Card onBoard(
      long boardId,
      String title,
      CardType type,
      boolean archived,
      Instant done,
      CardStatus status,
      Long derivedFromCardId) {
    return new Card(
        1L,
        boardId,
        20L,
        42,
        title,
        "Text",
        3,
        archived,
        done,
        1L,
        FIXED,
        FIXED,
        type,
        null,
        null,
        null,
        PROJECT,
        "ext:1",
        derivedFromCardId,
        null,
        status);
  }

  @Test
  void findOnBoardByNumber_liefertDieArchivierteKarteDesBoards() {
    Instant done = Instant.parse("2026-02-03T04:05:06Z");
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(cards.findByProjectIdAndNumber(PROJECT, 42))
        .thenReturn(
            Optional.of(onBoard(BOARD, "[Plan] Archiv", CardType.CARD, true, done, null, 7L)));
    when(cards.findById(7L))
        .thenReturn(Optional.of(card(7L, 20L, 40, false, null, CardType.CARD, null, null)));
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "In Review", 3));

    CardSearchService.BoardCardView view = service.findOnBoardByNumber(5L, BOARD, 42);

    assertThat(view.item().id()).isEqualTo(1L);
    assertThat(view.item().number()).isEqualTo(42);
    assertThat(view.item().title()).isEqualTo("[Plan] Archiv");
    assertThat(view.item().description()).isEqualTo("Text");
    assertThat(view.item().columnId()).isEqualTo(20L);
    assertThat(view.item().positionInColumn()).isEqualTo(3);
    assertThat(view.item().epic()).isFalse();
    assertThat(view.item().externalKey()).isEqualTo("ext:1");
    assertThat(view.item().derivedFrom()).isEqualTo(40);
    assertThat(view.item().status()).isNull();
    assertThat(view.kind()).isEqualTo("PLAN");
    assertThat(view.column()).isEqualTo("IN_REVIEW");
    assertThat(view.archived()).isTrue();
    assertThat(view.createdAt()).isEqualTo(FIXED);
    assertThat(view.movedToDoneAt()).isEqualTo(done);
  }

  @Test
  void findOnBoardByNumber_arbeitspaket_traegtSeinenStatusAlsSpaltenschluessel() {
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(cards.findByProjectIdAndNumber(PROJECT, 42))
        .thenReturn(
            Optional.of(
                onBoard(BOARD, "Paket", CardType.CARD, false, null, CardStatus.READY, null)));
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Eigene Spalte", 3));

    CardSearchService.BoardCardView view = service.findOnBoardByNumber(5L, BOARD, 42);

    assertThat(view.kind()).isEqualTo("ARBEITSPAKET");
    assertThat(view.column()).isEqualTo("READY");
    assertThat(view.item().status()).isEqualTo("READY");
    assertThat(view.item().derivedFrom()).isNull();
    assertThat(view.archived()).isFalse();
    assertThat(view.movedToDoneAt()).isNull();
  }

  @Test
  void findOnBoardByNumber_vorhaben_istEpicDerArtVorhaben() {
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(cards.findByProjectIdAndNumber(PROJECT, 42))
        .thenReturn(Optional.of(onBoard(BOARD, "Archiv", CardType.EPIC, false, null, null, null)));
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    CardSearchService.BoardCardView view = service.findOnBoardByNumber(5L, BOARD, 42);

    assertThat(view.item().epic()).isTrue();
    assertThat(view.kind()).isEqualTo("VORHABEN");
    assertThat(view.column()).isEqualTo("BACKLOG");
  }

  @Test
  void findOnBoardByNumber_prueftDieMitgliedschaftVorDemLookup() {
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    doThrow(new ProjectNotFoundException()).when(permissions).requireMembership(5L, PROJECT);

    assertThatThrownBy(() -> service.findOnBoardByNumber(5L, BOARD, 42))
        .isInstanceOf(ProjectNotFoundException.class);
    verify(cards, never()).findByProjectIdAndNumber(anyLong(), anyInt());
  }

  @Test
  void findOnBoardByNumber_karteAufAnderemBoard_istNichtGefunden() {
    // Board-Grenze (A3): dieselbe Nummer im selben Projekt, aber auf einem anderen Board. Die
    // Board-ID liegt jenseits des Long-Caches, damit ein Referenzvergleich auffiele.
    long board = 1000L;
    when(boardService.requireProjectId(board)).thenReturn(PROJECT);
    when(cards.findByProjectIdAndNumber(PROJECT, 42))
        .thenReturn(Optional.of(onBoard(1001L, "Fremd", CardType.CARD, false, null, null, null)));

    assertThatThrownBy(() -> service.findOnBoardByNumber(5L, board, 42))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void findOnBoardByNumber_gleicheBoardIdJenseitsDesLongCaches_istGefunden() {
    long board = 1000L;
    when(boardService.requireProjectId(board)).thenReturn(PROJECT);
    when(cards.findByProjectIdAndNumber(PROJECT, 42))
        .thenReturn(
            Optional.of(
                onBoard(Long.valueOf(1000L), "Eigen", CardType.CARD, false, null, null, null)));
    when(boardService.requireColumn(20L, board)).thenReturn(column(20L, "Backlog", 0));

    assertThat(service.findOnBoardByNumber(5L, board, 42).item().title()).isEqualTo("Eigen");
  }

  @Test
  void findOnBoardByNumber_unbekannteOderPapierkorbNummer_istNichtGefunden() {
    // Der Port filtert den Papierkorb (deleted_at IS NULL): Fuer ihn sehen beide Faelle gleich aus.
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(cards.findByProjectIdAndNumber(PROJECT, 99)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findOnBoardByNumber(5L, BOARD, 99))
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

    List<CardSearchService.CardSearchHit> hits = service.searchByNumber(5L, 42);

    assertThat(hits)
        .singleElement()
        .isEqualTo(
            new CardSearchService.CardSearchHit(
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

    List<CardSearchService.CardSearchHit> hits = service.searchByNumber(5L, 42);

    assertThat(hits)
        .extracting(CardSearchService.CardSearchHit::projectId)
        .containsExactly(PROJECT, PROJECT_B);
    assertThat(hits)
        .extracting(CardSearchService.CardSearchHit::projectName)
        .containsExactly("Projekt A", "Projekt B");
    assertThat(hits)
        .extracting(CardSearchService.CardSearchHit::boardName)
        .containsExactly("Board A", "Board B");
    assertThat(hits)
        .extracting(CardSearchService.CardSearchHit::columnName)
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
  void searchByNumber_reportsArchivedBoard_withName() {
    // Die Karte bleibt auffindbar, obwohl das Board ueber die normale Board-API 404 liefert.
    when(projects.listAccessible(5L)).thenReturn(List.of(accessible(PROJECT, "Projekt A")));
    when(cards.findByNumberInProjects(42, List.of(PROJECT)))
        .thenReturn(List.of(cardIn(1L, PROJECT, BOARD, 20L, 42)));
    when(boardService.requireBoardSummary(BOARD))
        .thenReturn(new BoardService.BoardSummary(BOARD, "Altes Board", true));
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Done", 4));

    CardSearchService.CardSearchHit hit = service.searchByNumber(5L, 42).get(0);

    assertThat(hit.boardName()).isEqualTo("Altes Board");
    assertThat(hit.boardArchived()).isTrue();
    assertThat(hit.columnName()).isEqualTo("Done");
  }

  @Test
  void searchByNumber_traegtCanSetStatusJeProjektDerKarte() {
    Card fremd =
        new Card(
            6L,
            BOARD_B,
            30L,
            7,
            "Fremd",
            null,
            0,
            false,
            null,
            1L,
            FIXED,
            FIXED,
            CardType.CARD,
            null,
            null,
            null,
            PROJECT_B,
            null,
            null,
            null,
            CardStatus.READY);
    when(projects.listAccessible(1L))
        .thenReturn(List.of(new ProjectService.AccessibleProject(PROJECT_B, "B")));
    when(cards.findByNumberInProjects(7, List.of(PROJECT_B))).thenReturn(List.of(fremd));
    when(boardService.requireBoardSummary(BOARD_B))
        .thenReturn(new BoardService.BoardSummary(BOARD_B, "Fremdes Board", false));
    when(boardService.requireColumn(30L, BOARD_B)).thenReturn(column(30L, "Ready", 1));
    when(permissions.hasPermission(1L, PROJECT, Permission.CARD_MOVE)).thenReturn(true);
    when(permissions.hasPermission(1L, PROJECT_B, Permission.CARD_MOVE)).thenReturn(false);

    CardSearchService.CardSearchHit treffer = service.searchByNumber(1L, 7).get(0);

    assertThat(treffer.card().status()).isEqualTo("READY");
    assertThat(treffer.card().canSetStatus()).isFalse();
  }
}
