package org.mwolff.manban.comment.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardRepository;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.domain.Card;
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
  private final CardRepository cards;
  private final BoardRepository boards;
  private final PermissionChecker permissions;
  private final AppUserRepository users;
  private final Clock clock;

  public CommentService(
      CommentRepository comments,
      CardRepository cards,
      BoardRepository boards,
      PermissionChecker permissions,
      AppUserRepository users,
      Clock clock) {
    this.comments = comments;
    this.cards = cards;
    this.boards = boards;
    this.permissions = permissions;
    this.users = users;
    this.clock = clock;
  }

  @Transactional
  public CommentView create(long userId, long cardId, String body) {
    long projectId = projectIdOfCard(cardId);
    permissions.require(userId, projectId, Permission.COMMENT_CREATE);
    String authorName = users.findById(userId).map(u -> u.displayName()).orElse("Unbekannt");
    Instant now = clock.instant();
    Comment saved = comments.save(new Comment(null, cardId, userId, authorName, body, now, now));
    return view(saved);
  }

  @Transactional(readOnly = true)
  public List<CommentView> list(long userId, long cardId) {
    permissions.requireMembership(userId, projectIdOfCard(cardId));
    return comments.findByCardId(cardId).stream().map(CommentService::view).toList();
  }

  @Transactional
  public CommentView update(long userId, long commentId, String body) {
    Comment comment = comments.findById(commentId).orElseThrow(CommentNotFoundException::new);
    permissions.require(userId, projectIdOfCard(comment.cardId()), Permission.COMMENT_UPDATE);
    // Bearbeiten darf nur der Autor selbst — auch ein Admin/Owner nicht fremde Kommentare.
    if (comment.authorUserId() == null || comment.authorUserId() != userId) {
      throw new ProjectAccessDeniedException();
    }
    return view(comments.save(comment.withBody(body)));
  }

  @Transactional
  public void delete(long userId, long commentId) {
    Comment comment = comments.findById(commentId).orElseThrow(CommentNotFoundException::new);
    // Löschen ist Moderation: nur Projekt-ADMIN/OWNER (COMMENT_DELETE), nicht der Autor allein.
    permissions.require(userId, projectIdOfCard(comment.cardId()), Permission.COMMENT_DELETE);
    comments.deleteById(comment.id());
  }

  private long projectIdOfCard(long cardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    Board board = boards.findById(card.boardId()).orElseThrow(BoardNotFoundException::new);
    return board.projectId();
  }

  private static CommentView view(Comment c) {
    return new CommentView(
        c.id(),
        c.cardId(),
        c.authorUserId(),
        c.authorName(),
        c.body(),
        c.createdAt(),
        c.updatedAt());
  }

  /** Kommentardarstellung. */
  public record CommentView(
      Long id,
      Long cardId,
      Long authorUserId,
      String authorName,
      String body,
      Instant createdAt,
      Instant updatedAt) {}
}
