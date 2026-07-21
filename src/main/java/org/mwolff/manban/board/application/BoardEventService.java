package org.mwolff.manban.board.application;

import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autorisierung für das Abonnieren des Live-Board-Streams: löst das Board auf und stellt die
 * Projekt-Mitgliedschaft sicher (analog zu den lesenden Board-Endpoints). Unbekanntes Board → 404,
 * Nichtmitglied → 404 (kein Existenz-Leak). Bewusst getrennt vom Web-/SSE-Plumbing, damit die
 * Autorisierung frameworkfrei testbar bleibt.
 */
@Service
public class BoardEventService {

  private final BoardRepository boards;
  private final PermissionChecker permissions;

  public BoardEventService(BoardRepository boards, PermissionChecker permissions) {
    this.boards = boards;
    this.permissions = permissions;
  }

  /** Wirft, wenn der Nutzer den Live-Stream des Boards nicht abonnieren darf. */
  @Transactional(readOnly = true)
  public void requireSubscribable(long userId, long boardId) {
    Board board = boards.findById(boardId).orElseThrow(BoardNotFoundException::new);
    permissions.requireMembership(userId, board.projectId());
  }
}
