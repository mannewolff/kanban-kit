package org.mwolff.manban.comment.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.auth.application.UserSummary;
import org.mwolff.manban.card.application.ActorContext;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.comment.domain.Comment;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kommentar-Use-Cases. Anlegen erfordert COMMENT_CREATE. <b>Bearbeiten</b> darf nur der Autor
 * selbst (COMMENT_UPDATE; auch ein Admin/Owner nicht fremde Kommentare). <b>Löschen</b> ist
 * Moderation und nur Projekt-ADMIN/OWNER vorbehalten (COMMENT_DELETE).
 */
@Service
public class CommentService {

  private final CommentRepository comments;
  private final CardService cardService;
  private final PermissionChecker permissions;
  private final UserLookup users;
  private final ActorContext actor;
  private final Clock clock;

  public CommentService(
      CommentRepository comments,
      CardService cardService,
      PermissionChecker permissions,
      UserLookup users,
      ActorContext actor,
      Clock clock) {
    this.comments = comments;
    this.cardService = cardService;
    this.permissions = permissions;
    this.users = users;
    this.actor = actor;
    this.clock = clock;
  }

  /** Legt einen Kommentar ohne Laufkennung an — der Session-Weg (Plan #1423, A3). */
  @Transactional
  public CommentView create(long userId, long cardId, String body) {
    return anlegen(userId, cardId, body, null);
  }

  /**
   * Legt einen Kommentar an und hält die Laufkennung des Schreibers fest (Issue #1428).
   *
   * @param laufStart Laufkennung aus dem Header {@code X-Night-Run}; {@code null} ohne Ausweis
   */
  @Transactional
  public CommentView create(long userId, long cardId, String body, @Nullable Instant laufStart) {
    return anlegen(userId, cardId, body, laufStart);
  }

  private CommentView anlegen(long userId, long cardId, String body, @Nullable Instant laufStart) {
    long projectId = cardService.requireProjectId(cardId);
    permissions.require(userId, projectId, Permission.COMMENT_CREATE);
    String authorName = users.findById(userId).map(UserSummary::displayName).orElse("Unbekannt");
    Instant now = clock.instant();
    // Herkunft aus derselben Quelle wie der Verlauf (Issue #1620, A4); ohne Stempel bleibt sie
    // leer — unbekannt, nicht geraten (A6).
    ActorStamp stamp = actor.current();
    Comment saved =
        comments.save(
            new Comment(
                null,
                cardId,
                userId,
                authorName,
                body,
                now,
                now,
                laufStart,
                stamp.originName(),
                stamp.tokenName(),
                stamp.agent()));
    return view(saved);
  }

  @Transactional(readOnly = true)
  public List<CommentView> list(long userId, long cardId) {
    permissions.requireMembership(userId, cardService.requireProjectId(cardId));
    return comments.findByCardId(cardId).stream().map(CommentService::view).toList();
  }

  /** Ändert einen Kommentar ohne Laufkennung — der Session-Weg (Plan #1423, A3). */
  @Transactional
  public CommentView update(long userId, long commentId, String body) {
    return aendern(userId, commentId, body, null);
  }

  /**
   * Ändert einen Kommentar und überschreibt seine Laufkennung mit der des aktuellen Schreibers,
   * auch mit {@code null} (Issue #1428, Plan #1423 E3): Der Laufstand gehört dem letzten Schreiber.
   */
  @Transactional
  public CommentView update(long userId, long commentId, String body, @Nullable Instant laufStart) {
    return aendern(userId, commentId, body, laufStart);
  }

  private CommentView aendern(
      long userId, long commentId, String body, @Nullable Instant laufStart) {
    Comment comment = comments.findById(commentId).orElseThrow(CommentNotFoundException::new);
    permissions.require(
        userId, cardService.requireProjectId(comment.cardId()), Permission.COMMENT_UPDATE);
    // Bearbeiten darf nur der Autor selbst — auch ein Admin/Owner nicht fremde Kommentare.
    Long author = comment.authorUserId();
    if (author == null || author != userId) {
      throw new ProjectAccessDeniedException();
    }
    return view(comments.save(comment.withBody(body, laufStart)));
  }

  @Transactional
  public void delete(long userId, long commentId) {
    Comment comment = comments.findById(commentId).orElseThrow(CommentNotFoundException::new);
    // Löschen ist Moderation: nur Projekt-ADMIN/OWNER (COMMENT_DELETE), nicht der Autor allein.
    permissions.require(
        userId, cardService.requireProjectId(comment.cardId()), Permission.COMMENT_DELETE);
    comments.deleteById(comment.requireId());
  }

  /**
   * Die Laufstand-Kommentare des Projekts, je Karte einer (Issue #1373, Plan #1372 E5): Karte, Text
   * und Laufkennung des letzten Schreibers (Issue #1428), <b>kein</b> {@code updatedAt} — {@link
   * #update} schreibt ihn nicht fort, und das Kit ersetzt den Laufstand über genau diesen Weg (A1).
   *
   * <p>Ohne Rechteprüfung: Vertrag für das Modul {@code nightrun}, das die Projekt-Rolle vor dem
   * Aufruf selbst prüft — wie {@code CardRunQueryService#existingCardNumbers}.
   */
  @Transactional(readOnly = true)
  public List<LaufstandView> laufstaendeImProjekt(long projectId) {
    return comments.findLaufstaendeImProjekt(projectId).stream()
        .map(c -> new LaufstandView(c.cardId(), c.body(), c.laufStart()))
        .toList();
  }

  /**
   * Je Karte des Boards der erste Kommentar, der den Suchbegriff enthält (Plan #1574, E3, E16) —
   * für die Textsuche der Kanban-Strecke. Erfordert Projekt-Mitgliedschaft (Leserecht); die Abfrage
   * bleibt auf Karten dieses Projekts und Boards ohne Papierkorb begrenzt, sodass ein Board eines
   * anderen Projekts nichts liefert.
   *
   * @param q Suchbegriff, getrimmt und nicht leer
   */
  @Transactional(readOnly = true)
  public List<KommentarTrefferView> trefferAufBoard(
      long userId, long projectId, long boardId, String q) {
    permissions.requireMembership(userId, projectId);
    return comments.findFirstMatchPerCardOnBoard(projectId, boardId, q).stream()
        .map(c -> new KommentarTrefferView(c.cardId(), c.requireId(), c.body()))
        .toList();
  }

  private static CommentView view(Comment c) {
    return new CommentView(
        c.requireId(),
        c.cardId(),
        c.authorUserId(),
        c.authorName(),
        c.body(),
        c.createdAt(),
        c.updatedAt(),
        c.origin(),
        c.tokenName(),
        c.agent());
  }

  /** Kommentardarstellung. */
  @Schema(description = "Ein Kommentar an einer Karte.")
  public record CommentView(
      @Schema(description = "Interne ID des Kommentars.", example = "455") Long id,
      @Schema(description = "Interne ID der Karte.", example = "812") Long cardId,
      @Schema(
              description =
                  "Benutzer-ID des Autors; null, wenn kein Benutzer als Autor festgehalten ist.",
              example = "7")
          @Nullable Long authorUserId,
      @Schema(description = "Anzeigename des Autors beim Anlegen.", example = "Erika Muster")
          String authorName,
      @Schema(description = "Text in Markdown.", example = "Sieht gut aus, bitte noch testen.")
          String body,
      @Schema(description = "Zeitpunkt der Anlage.") Instant createdAt,
      @Schema(description = "Zeitpunkt der letzten Änderung.") Instant updatedAt,
      @Schema(
              description =
                  "Herkunft beim Anlegen: SESSION (Oberfläche) oder TOKEN (Projekt-Zugang); null,"
                      + " wenn sie unbekannt ist.",
              example = "TOKEN")
          @Nullable String origin,
      @Schema(
              description = "Name des Projekt-Zugangs bei Herkunft TOKEN; sonst null.",
              example = "kit-nacht")
          @Nullable String tokenName,
      @Schema(
              description =
                  "Modell-Selbstauskunft des Werkzeugs (Header X-Agent-Model); null ohne Angabe.",
              example = "claude-opus-5-5")
          @Nullable String agent) {}

  /**
   * Der Laufstand-Kommentar einer Karte (Issue #1373).
   *
   * @param cardId Karte, an der der Kommentar steht
   * @param body Kommentartext, beginnend mit {@code ## Laufstand}
   * @param laufStart Laufkennung des letzten Schreibers (Issue #1428); {@code null} ohne Ausweis
   */
  public record LaufstandView(long cardId, String body, @Nullable Instant laufStart) {}

  /**
   * Der erste passende Kommentar einer Karte in der Textsuche (Plan #1574, E16).
   *
   * @param cardId Karte, an der der Kommentar steht
   * @param commentId der Kommentar
   * @param body sein Text
   */
  public record KommentarTrefferView(long cardId, long commentId, String body) {}
}
