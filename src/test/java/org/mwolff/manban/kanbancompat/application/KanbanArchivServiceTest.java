package org.mwolff.manban.kanbancompat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.CardIngestService;
import org.mwolff.manban.card.application.CardIngestService.BoardItemView;
import org.mwolff.manban.card.application.CardMoveService;
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.card.application.CardSearchService;
import org.mwolff.manban.card.application.CardSearchService.BoardCardView;
import org.mwolff.manban.card.application.CardSearchService.SuchSeite;
import org.mwolff.manban.card.application.CardSearchService.SuchTreffer;
import org.mwolff.manban.card.application.CardSearchService.Suchanfrage;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.EpicService;
import org.mwolff.manban.card.application.LabelService;
import org.mwolff.manban.comment.application.CommentService;
import org.mwolff.manban.comment.application.CommentService.CommentView;
import org.mwolff.manban.kanbancompat.application.KanbanArchivService.Excerpt;
import org.mwolff.manban.kanbancompat.application.KanbanArchivService.ItemDetail;
import org.mwolff.manban.kanbancompat.application.KanbanArchivService.SearchHit;
import org.mwolff.manban.kanbancompat.application.KanbanArchivService.SearchPage;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Comment;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Item;

/** Unit-Tests des lesenden Archivzugangs der Kanban-Strecke (Plan #1574). */
class KanbanArchivServiceTest {

  private static final long BOARD = 10L;
  private static final long USER = 1L;
  private static final Instant ANGELEGT = Instant.parse("2026-01-02T03:04:05Z");
  private static final Instant ERLEDIGT = Instant.parse("2026-02-03T04:05:06Z");

  private BoardService boardService;
  private LabelService labelService;
  private CommentService commentService;
  private CardSearchService search;
  private KanbanArchivService service;

  private static KanbanPrincipal bound() {
    return new KanbanPrincipal(USER, 2L, 5L, BOARD, "Token");
  }

  @BeforeEach
  void setUp() {
    boardService = mock(BoardService.class);
    labelService = mock(LabelService.class);
    commentService = mock(CommentService.class);
    search = mock(CardSearchService.class);
    KanbanCompatService compat =
        new KanbanCompatService(
            boardService,
            mock(CardService.class),
            mock(CardIngestService.class),
            mock(CardMoveService.class),
            mock(EpicService.class),
            labelService,
            commentService,
            new IdempotencyGuard(new InMemoryIdempotencyRecordStore(), Clock.systemUTC()));
    service = new KanbanArchivService(compat, search, commentService);
  }

  @Test
  void itemByNumber_liefertDieArchivierteKarteMitAllenKommentaren() {
    BoardItemView karte =
        new BoardItemView(77L, 42, "[Plan] Archiv", "Text", 103L, 2, false, "ext:1", 40, null);
    when(search.findOnBoardByNumber(USER, BOARD, 42))
        .thenReturn(new BoardCardView(karte, "PLAN", "IN_REVIEW", true, ANGELEGT, ERLEDIGT));
    when(boardService.listColumns(BOARD))
        .thenReturn(
            List.of(
                new ColumnView(100L, "Backlog", 0, null),
                new ColumnView(103L, "In Review", 3, null)));
    when(labelService.namesByCard(BOARD, List.of(77L)))
        .thenReturn(Map.of(77L, List.of("kit:nightrun")));
    Instant erster = Instant.parse("2026-01-05T00:00:00Z");
    Instant zweiter = Instant.parse("2026-01-06T00:00:00Z");
    when(commentService.list(USER, 77L))
        .thenReturn(
            List.of(
                new CommentView(
                    5L, 77L, 3L, "Manne", "## Review", erster, erster, "SESSION", null, null),
                new CommentView(
                    6L,
                    77L,
                    null,
                    "Nachtlauf",
                    "## Abschluss",
                    zweiter,
                    zweiter,
                    "TOKEN",
                    "kit",
                    "opus")));

    ItemDetail detail = service.itemByNumber(bound(), 42);

    assertThat(detail.item())
        .isEqualTo(
            new Item(
                77L,
                42,
                "[Plan] Archiv",
                "Text",
                "IN_REVIEW",
                2,
                "card",
                List.of("kit:nightrun"),
                "ext:1",
                40));
    assertThat(detail.archived()).isTrue();
    assertThat(detail.createdAt()).isEqualTo(ANGELEGT);
    assertThat(detail.movedToDoneAt()).isEqualTo(ERLEDIGT);
    assertThat(detail.comments())
        .containsExactly(
            new Comment(5L, "Manne", "## Review", erster),
            new Comment(6L, "Nachtlauf", "## Abschluss", zweiter));
  }

  @Test
  void itemByNumber_vorhabenOhneKommentare_istEinEpicMitLeererListe() {
    BoardItemView vorhaben =
        new BoardItemView(78L, 43, "Archiv", null, 100L, 0, true, null, null, null);
    when(search.findOnBoardByNumber(USER, BOARD, 43))
        .thenReturn(new BoardCardView(vorhaben, "VORHABEN", "BACKLOG", false, ANGELEGT, null));
    when(boardService.listColumns(BOARD))
        .thenReturn(List.of(new ColumnView(100L, "Backlog", 0, null)));
    when(labelService.namesByCard(BOARD, List.of(78L))).thenReturn(Map.of());
    when(commentService.list(USER, 78L)).thenReturn(List.of());

    ItemDetail detail = service.itemByNumber(bound(), 43);

    assertThat(detail.item().type()).isEqualTo("epic");
    assertThat(detail.item().labels()).isEmpty();
    assertThat(detail.archived()).isFalse();
    assertThat(detail.movedToDoneAt()).isNull();
    assertThat(detail.comments()).isEmpty();
  }

  @Test
  void itemByNumber_karteFehlt_reichtDas404Durch_ohneKommentareZuLesen() {
    when(search.findOnBoardByNumber(USER, BOARD, 99)).thenThrow(new CardNotFoundException());

    assertThatThrownBy(() -> service.itemByNumber(bound(), 99))
        .isInstanceOf(CardNotFoundException.class);
    verify(commentService, never()).list(anyLong(), anyLong());
  }

  @Test
  void itemByNumber_ohneBindung_istTokenNotBound() {
    KanbanPrincipal unbound = new KanbanPrincipal(USER, 2L, null, null, "Token");

    assertThatThrownBy(() -> service.itemByNumber(unbound, 42))
        .isInstanceOf(TokenNotBoundException.class);
    assertThatThrownBy(() -> service.itemByNumber(null, 42))
        .isInstanceOf(TokenNotBoundException.class);
    verify(search, never()).findOnBoardByNumber(anyLong(), anyLong(), anyInt());
  }

  // --- search (Plan #1574, E6, E10) ---

  @Test
  void search_reichtDieFilterDurch_undFormtDieTreffer() {
    Instant von = Instant.parse("2026-01-01T00:00:00Z");
    Instant bis = Instant.parse("2026-06-01T00:00:00Z");
    Suchanfrage erwartet = new Suchanfrage(null, Set.of("PLAN", "FACHLICH"), 12, von, bis, 90, 5);
    when(search.searchOnBoard(USER, BOARD, erwartet, Map.of()))
        .thenReturn(
            new SuchSeite(
                List.of(
                    new SuchTreffer(
                        77L,
                        42,
                        "[Plan] Archiv",
                        "PLAN",
                        "DONE",
                        true,
                        ANGELEGT,
                        ERLEDIGT,
                        "Anfang",
                        "BODY",
                        null),
                    new SuchTreffer(
                        78L,
                        41,
                        "[Fachlich] F",
                        "FACHLICH",
                        "BACKLOG",
                        false,
                        ANGELEGT,
                        null,
                        "Review",
                        "COMMENT",
                        9L)),
                41));

    SearchPage page =
        service.search(bound(), null, List.of("PLAN", "FACHLICH", "PLAN"), 12, von, bis, 90, 5);

    assertThat(page.hits())
        .containsExactly(
            new SearchHit(
                77L,
                42,
                "[Plan] Archiv",
                "PLAN",
                "DONE",
                true,
                ANGELEGT,
                ERLEDIGT,
                new Excerpt("Anfang", "BODY", null)),
            new SearchHit(
                78L,
                41,
                "[Fachlich] F",
                "FACHLICH",
                "BACKLOG",
                false,
                ANGELEGT,
                null,
                new Excerpt("Review", "COMMENT", 9L)));
    assertThat(page.nextCursor()).isEqualTo("41");
  }

  @Test
  void search_ohneFilter_undLetzterTeil_hatKeinenNaechstenCursor() {
    when(search.searchOnBoard(
            USER, BOARD, new Suchanfrage(null, Set.of(), null, null, null, null, 50), Map.of()))
        .thenReturn(new SuchSeite(List.of(), null));

    SearchPage page = service.search(bound(), null, null, null, null, null, null, 50);

    assertThat(page.hits()).isEmpty();
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  void search_ohneBindung_istTokenNotBound() {
    KanbanPrincipal unbound = new KanbanPrincipal(USER, 2L, null, null, "Token");

    assertThatThrownBy(() -> service.search(unbound, "x", List.of(), null, null, null, null, 50))
        .isInstanceOf(TokenNotBoundException.class);
    verify(search, never()).searchOnBoard(anyLong(), anyLong(), any(), any());
    verify(commentService, never()).trefferAufBoard(anyLong(), anyLong(), anyLong(), any());
  }

  // --- Textsuche (Plan #1574, A5, E16) ---

  @Test
  void search_mitSuchbegriff_holtDieKommentartrefferUndReichtSieDurch() {
    when(commentService.trefferAufBoard(USER, 5L, BOARD, "Review"))
        .thenReturn(
            List.of(
                new CommentService.KommentarTrefferView(77L, 9L, "Ein Review"),
                new CommentService.KommentarTrefferView(78L, 11L, "Noch ein Review")));
    Suchanfrage erwartet = new Suchanfrage(" Review ", Set.of(), null, null, null, null, 50);
    when(search.searchOnBoard(
            USER,
            BOARD,
            erwartet,
            Map.of(
                77L, new CardSearchService.KommentarFund(9L, "Ein Review"),
                78L, new CardSearchService.KommentarFund(11L, "Noch ein Review"))))
        .thenReturn(new SuchSeite(List.of(), null));

    SearchPage page = service.search(bound(), " Review ", null, null, null, null, null, 50);

    assertThat(page.hits()).isEmpty();
  }

  @Test
  void search_ohneOderMitLeeremSuchbegriff_fragtDasKommentarmodulNicht() {
    when(search.searchOnBoard(anyLong(), anyLong(), any(), any()))
        .thenReturn(new SuchSeite(List.of(), null));

    service.search(bound(), null, null, null, null, null, null, 50);
    service.search(bound(), "   ", null, null, null, null, null, 50);

    verify(commentService, never()).trefferAufBoard(anyLong(), anyLong(), anyLong(), any());
    verify(search)
        .searchOnBoard(
            USER, BOARD, new Suchanfrage("   ", Set.of(), null, null, null, null, 50), Map.of());
  }

  // --- lineage (Plan #1574, E10, E11) ---

  @Test
  void lineage_formtKarteVorfahrenUndNachfahren_lueckeNurMitNummerUndTiefe() {
    when(search.lineageOnBoard(USER, BOARD, 30))
        .thenReturn(
            new CardSearchService.Kette(
                new CardSearchService.KettenGlied(
                    30, "Paket", "ARBEITSPAKET", "READY", false, 0, false),
                List.of(
                    new CardSearchService.KettenGlied(20, null, null, null, false, 1, true),
                    new CardSearchService.KettenGlied(
                        10, "[Fachlich] Anlass", "FACHLICH", "DONE", true, 2, false)),
                List.of(
                    new CardSearchService.KettenGlied(
                        40, "Kind", "ARBEITSPAKET", "BACKLOG", false, 1, false))));

    KanbanArchivService.Lineage lineage = service.lineage(bound(), 30);

    assertThat(lineage.card())
        .isEqualTo(
            new KanbanArchivService.LineageNode(
                30, "Paket", "ARBEITSPAKET", "READY", false, 0, false));
    assertThat(lineage.ancestors())
        .containsExactly(
            new KanbanArchivService.LineageNode(20, null, null, null, false, 1, true),
            new KanbanArchivService.LineageNode(
                10, "[Fachlich] Anlass", "FACHLICH", "DONE", true, 2, false));
    assertThat(lineage.descendants())
        .containsExactly(
            new KanbanArchivService.LineageNode(
                40, "Kind", "ARBEITSPAKET", "BACKLOG", false, 1, false));
  }

  @Test
  void lineage_karteFehlt_reichtDas404Durch() {
    when(search.lineageOnBoard(USER, BOARD, 99)).thenThrow(new CardNotFoundException());

    assertThatThrownBy(() -> service.lineage(bound(), 99))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void lineage_ohneBindung_istTokenNotBound() {
    KanbanPrincipal unbound = new KanbanPrincipal(USER, 2L, null, null, "Token");

    assertThatThrownBy(() -> service.lineage(unbound, 30))
        .isInstanceOf(TokenNotBoundException.class);
    verify(search, never()).lineageOnBoard(anyLong(), anyLong(), anyInt());
  }
}
