package org.mwolff.manban.kanbancompat.application;

import static org.mwolff.manban.kanbancompat.application.KanbanBindung.requireBound;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.card.application.CardSearchService;
import org.mwolff.manban.card.application.CardSearchService.BoardCardView;
import org.mwolff.manban.comment.application.CommentService;
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
  private final CardSearchService search;
  private final CommentService commentService;

  public KanbanArchivService(
      KanbanCompatService compat, CardSearchService search, CommentService commentService) {
    this.compat = compat;
    this.search = search;
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
    BoardCardView karte = search.findOnBoardByNumber(userId, boardId, number);
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
}
