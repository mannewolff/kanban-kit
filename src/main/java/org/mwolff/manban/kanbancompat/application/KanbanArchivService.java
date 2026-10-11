package org.mwolff.manban.kanbancompat.application;

import static org.mwolff.manban.kanbancompat.application.KanbanBindung.requireBound;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.card.application.CardSearchService;
import org.mwolff.manban.card.application.CardSearchService.BoardCardView;
import org.mwolff.manban.card.application.CardSearchService.Kette;
import org.mwolff.manban.card.application.CardSearchService.KettenGlied;
import org.mwolff.manban.card.application.CardSearchService.KommentarFund;
import org.mwolff.manban.card.application.CardSearchService.SuchSeite;
import org.mwolff.manban.card.application.CardSearchService.SuchTreffer;
import org.mwolff.manban.card.application.CardSearchService.Suchanfrage;
import org.mwolff.manban.comment.application.CommentService;
import org.mwolff.manban.comment.application.CommentService.KommentarTrefferView;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Comment;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Item;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lesender Zugang der Kanban-Strecke zum ganzen Bestand des gebundenen Boards, archivierte Karten
 * eingeschlossen (Plan #1574). Ein eigener Service neben {@link KanbanCompatService}, weil jener an
 * der PMD-Grenze {@code TooManyMethods} steht (A6).
 *
 * <p>Reichweite wie die bestehenden Leseaufrufe (A3): nur mit gebundenem Token, nur das gebundene
 * Board, Papierkorb unsichtbar. Die Item-Form kommt aus {@link KanbanCompatService#item}, damit
 * Einzelabruf und Übersicht dieselben Angaben liefern (E19).
 */
@Service
public class KanbanArchivService {

  private final KanbanCompatService compat;
  private final CardSearchService cardSearch;
  private final CommentService commentService;

  public KanbanArchivService(
      KanbanCompatService compat, CardSearchService search, CommentService commentService) {
    this.compat = compat;
    this.cardSearch = search;
    this.commentService = commentService;
  }

  /**
   * Eine Karte des gebundenen Boards nach ihrer projektweiten Nummer, auch archiviert, mit allen
   * Kommentaren in zeitlicher Reihenfolge (Kriterium 1). Eine unbekannte Nummer, eine Nummer auf
   * einem anderen Board und eine im Papierkorb enden gleich mit 404.
   */
  @Transactional(readOnly = true)
  public ItemDetail itemByNumber(KanbanPrincipal principal, int number) {
    long boardId = requireBound(principal);
    long userId = principal.userId();
    BoardCardView karte = cardSearch.findOnBoardByNumber(userId, boardId, number);
    List<Comment> comments =
        commentService.list(userId, karte.item().id()).stream()
            .map(c -> new Comment(c.id(), c.authorName(), c.body(), c.createdAt()))
            .toList();
    return new ItemDetail(
        compat.item(boardId, karte.item()),
        karte.archived(),
        karte.createdAt(),
        karte.movedToDoneAt(),
        comments);
  }

  /**
   * Durchsucht das gebundene Board, archivierte Karten eingeschlossen, nach Text, Art, Vorhaben und
   * Zeitraum des Anlagedatums, in Teilen nach Nummer absteigend (Kriterien 2 und 4). Mit
   * Suchbegriff holt der Dienst die Kommentar-Fundstellen aus dem Modul {@code comment} und reicht
   * sie an die Kartensuche (A5); ohne Suchbegriff fragt er das Modul nicht und der Auszug ist der
   * Anfang des Texts (E5).
   *
   * @param q Suchbegriff für Titel, Text und Kommentare; leer oder fehlend heißt kein Textfilter
   * @param kinds Arten, ODER-verknüpft; fehlend oder leer heißt jede Art
   * @param cursor Nummer des letzten Treffers des vorigen Teils
   */
  @Transactional(readOnly = true)
  public SearchPage search(
      KanbanPrincipal principal,
      @Nullable String q,
      @Nullable List<String> kinds,
      @Nullable Integer epic,
      @Nullable Instant createdFrom,
      @Nullable Instant createdTo,
      @Nullable Integer cursor,
      int limit) {
    long boardId = requireBound(principal);
    String suchbegriff = q == null ? "" : q.trim();
    Map<Long, KommentarFund> kommentarFunde =
        suchbegriff.isEmpty()
            ? Map.of()
            : commentService
                .trefferAufBoard(
                    principal.userId(),
                    Objects.requireNonNull(principal.projectId()),
                    boardId,
                    suchbegriff)
                .stream()
                .collect(
                    Collectors.toMap(
                        KommentarTrefferView::cardId,
                        t -> new KommentarFund(t.commentId(), t.body())));
    Suchanfrage anfrage =
        new Suchanfrage(
            q,
            kinds == null ? Set.of() : Set.copyOf(kinds),
            epic,
            createdFrom,
            createdTo,
            cursor,
            limit);
    SuchSeite seite =
        cardSearch.searchOnBoard(principal.userId(), boardId, anfrage, kommentarFunde);
    Integer naechster = seite.naechsterCursor();
    return new SearchPage(
        seite.treffer().stream().map(KanbanArchivService::hit).toList(),
        naechster == null ? null : String.valueOf(naechster));
  }

  /**
   * Die Herkunftskette einer Karte des gebundenen Boards nach ihrer projektweiten Nummer: Vorfahren
   * bis zur Wurzel, Nachfahren bis zu den Blättern, archivierte Glieder eingeschlossen (Kriterium
   * 3, E11). Ein Glied, das es nicht mehr gibt oder das außerhalb der Sicht liegt, erscheint als
   * Lücke mit Nummer und Tiefe (E12). Eine Nummer im Papierkorb, auf einem anderen Board oder eine
   * unbekannte enden gleich mit 404.
   */
  @Transactional(readOnly = true)
  public Lineage lineage(KanbanPrincipal principal, int number) {
    long boardId = requireBound(principal);
    Kette kette = cardSearch.lineageOnBoard(principal.userId(), boardId, number);
    return new Lineage(
        node(kette.karte()),
        kette.vorfahren().stream().map(KanbanArchivService::node).toList(),
        kette.nachfahren().stream().map(KanbanArchivService::node).toList());
  }

  private static LineageNode node(KettenGlied g) {
    return new LineageNode(
        g.number(), g.title(), g.kind(), g.column(), g.archived(), g.depth(), g.gap());
  }

  private static SearchHit hit(SuchTreffer t) {
    return new SearchHit(
        t.id(),
        t.number(),
        t.title(),
        t.kind(),
        t.column(),
        t.archived(),
        t.createdAt(),
        t.movedToDoneAt(),
        new Excerpt(t.auszug(), t.fundstelle(), t.kommentarId()));
  }

  // --- Response-Formen (Plan #1574, E10) ------------------------------------

  /** Eine Karte mit Archiv-Kennzeichen, Zeitpunkten und allen Kommentaren. */
  @Schema(description = "Eine Karte des gebundenen Boards, auch archiviert, mit Kommentaren.")
  public record ItemDetail(
      @Schema(description = "Die Karte in derselben Form wie in GET /api/kanban/items.") Item item,
      @Schema(description = "true, wenn die Karte archiviert ist.", example = "true")
          boolean archived,
      @Schema(description = "Zeitpunkt der Anlage.", example = "2026-09-01T08:00:00Z")
          Instant createdAt,
      @Schema(
              description = "Zeitpunkt des Zugs nach Done; fehlt, wenn es keinen gab.",
              example = "2026-09-20T17:30:00Z")
          @Nullable Instant movedToDoneAt,
      @Schema(description = "Alle Kommentare der Karte, der älteste zuerst.")
          List<Comment> comments) {}

  /** Ein Teil des Suchergebnisses. */
  @Schema(description = "Ein Teil der Treffer einer Suche, nach Nummer absteigend.")
  public record SearchPage(
      @Schema(description = "Die Treffer dieses Teils, höchstens limit viele.")
          List<SearchHit> hits,
      @Schema(
              description =
                  "Wert für den Parameter cursor des nächsten Teils; fehlt, wenn keine Treffer"
                      + " mehr folgen.",
              example = "1403")
          @Nullable String nextCursor) {}

  /** Eine gefundene Karte mit Art, Ort, Zeitpunkten und Auszug. */
  @Schema(description = "Eine gefundene Karte des gebundenen Boards, auch archiviert.")
  public record SearchHit(
      @Schema(description = "Interne ID der Karte.", example = "4711") long id,
      @Schema(description = "Projektweite Nummer der Karte.", example = "1403") int number,
      @Schema(description = "Titel der Karte.", example = "[Fachlich] Export als CSV") String title,
      @Schema(
              description =
                  "Art der Karte: FACHLICH, PLAN, IDEE, TASK oder MENSCH nach dem Titel-Präfix,"
                      + " ARBEITSPAKET ohne Präfix, VORHABEN für ein Vorhaben.",
              example = "FACHLICH")
          String kind,
      @Schema(
              description =
                  "Spaltenschlüssel wie in GET /api/kanban/items, auch bei einer archivierten"
                      + " Karte.",
              example = "DONE")
          String column,
      @Schema(description = "true, wenn die Karte archiviert ist.", example = "true")
          boolean archived,
      @Schema(description = "Zeitpunkt der Anlage.", example = "2026-09-01T08:00:00Z")
          Instant createdAt,
      @Schema(
              description = "Zeitpunkt des Zugs nach Done; fehlt, wenn es keinen gab.",
              example = "2026-09-20T17:30:00Z")
          @Nullable Instant movedToDoneAt,
      @Schema(description = "Kurzer Auszug aus der Karte.") Excerpt excerpt) {}

  /** Der Auszug eines Treffers und seine Fundstelle. */
  @Schema(description = "Kurzer Auszug aus der Karte und wo er steht.")
  public record Excerpt(
      @Schema(
              description = "Höchstens 200 Zeichen, Zeilenumbrüche als Leerzeichen.",
              example = "Wer einen neuen Fachplan schreibt, soll frühere finden …")
          String text,
      @Schema(
              description =
                  "Fundstelle: TITLE (Titel), BODY (Text) oder COMMENT (ein Kommentar). Ohne"
                      + " Suchbegriff BODY mit dem Anfang des Texts.",
              example = "BODY")
          String source,
      @Schema(description = "ID des Kommentars bei COMMENT; fehlt sonst.", example = "88")
          @Nullable Long commentId) {}

  /** Die Herkunftskette einer Karte. */
  @Schema(description = "Die Herkunftskette einer Karte des gebundenen Boards.")
  public record Lineage(
      @Schema(description = "Die angefragte Karte selbst, Tiefe 0.") LineageNode card,
      @Schema(
              description =
                  "Vorfahren von der Karte aufwärts bis zur Wurzel, Tiefe 1, 2, …; archivierte"
                      + " eingeschlossen.")
          List<LineageNode> ancestors,
      @Schema(
              description =
                  "Nachfahren in Präorder, Geschwister nach Nummer, Tiefe 1, 2, …; archivierte"
                      + " eingeschlossen. Nachfahren im Papierkorb oder auf einem anderen Board"
                      + " fehlen.")
          List<LineageNode> descendants) {}

  /** Ein Glied der Herkunftskette, auch eine Lücke. */
  @Schema(
      description =
          "Ein Glied der Herkunftskette. Bei einer Lücke (gap = true) sind nur number und depth"
              + " gesetzt.")
  public record LineageNode(
      @Schema(description = "Projektweite Nummer der Karte.", example = "1403")
          @Nullable Integer number,
      @Schema(
              description = "Titel der Karte; fehlt bei einer Lücke.",
              example = "[Fachlich] Export als CSV")
          @Nullable String title,
      @Schema(
              description = "Art der Karte wie in GET /api/kanban/search; fehlt bei einer Lücke.",
              example = "FACHLICH")
          @Nullable String kind,
      @Schema(
              description =
                  "Spaltenschlüssel wie in GET /api/kanban/items, auch bei einer archivierten"
                      + " Karte; fehlt bei einer Lücke.",
              example = "DONE")
          @Nullable String column,
      @Schema(description = "true, wenn die Karte archiviert ist.", example = "true")
          boolean archived,
      @Schema(description = "Abstand zur angefragten Karte: 0 für die Karte selbst.", example = "1")
          int depth,
      @Schema(
              description =
                  "true für ein Glied, das es nicht mehr gibt oder das außerhalb des Boards"
                      + " liegt: im Papierkorb, auf einem anderen Board oder endgültig gelöscht.",
              example = "false")
          boolean gap) {}
}
