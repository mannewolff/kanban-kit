package org.mwolff.manban.card.application;

import java.util.List;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardRepository;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.card.domain.Label;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verwaltung der board-scoped Labels. Anlegen/Bearbeiten/Löschen ist ein Board-Recht ({@link
 * Permission#BOARD_UPDATE}); Auflisten steht jedem Mitglied offen.
 */
@Service
public class LabelService {

  private final LabelRepository labels;
  private final BoardRepository boards;
  private final PermissionChecker permissions;

  public LabelService(
      LabelRepository labels, BoardRepository boards, PermissionChecker permissions) {
    this.labels = labels;
    this.boards = boards;
    this.permissions = permissions;
  }

  @Transactional(readOnly = true)
  public List<Label> list(long userId, long boardId) {
    Board board = boards.findById(boardId).orElseThrow(BoardNotFoundException::new);
    permissions.requireMembership(userId, board.projectId());
    return labels.findByBoardId(boardId);
  }

  @Transactional
  public Label create(long userId, long boardId, String name, String color) {
    Board board = boards.findById(boardId).orElseThrow(BoardNotFoundException::new);
    permissions.require(userId, board.projectId(), Permission.BOARD_UPDATE);
    String trimmed = requireName(name);
    if (labels.existsByBoardIdAndName(boardId, trimmed)) {
      throw new InvalidLabelException("Label existiert bereits: " + trimmed);
    }
    return labels.save(new Label(null, boardId, trimmed, color));
  }

  @Transactional
  public Label update(long userId, long labelId, String name, String color) {
    Label label = labels.findById(labelId).orElseThrow(LabelNotFoundException::new);
    Board board = boards.findById(label.boardId()).orElseThrow(BoardNotFoundException::new);
    permissions.require(userId, board.projectId(), Permission.BOARD_UPDATE);
    String trimmed = requireName(name);
    if (!trimmed.equals(label.name()) && labels.existsByBoardIdAndName(label.boardId(), trimmed)) {
      throw new InvalidLabelException("Label existiert bereits: " + trimmed);
    }
    return labels.save(label.withContent(trimmed, color));
  }

  @Transactional
  public void delete(long userId, long labelId) {
    Label label = labels.findById(labelId).orElseThrow(LabelNotFoundException::new);
    Board board = boards.findById(label.boardId()).orElseThrow(BoardNotFoundException::new);
    permissions.require(userId, board.projectId(), Permission.BOARD_UPDATE);
    labels.deleteById(labelId);
  }

  private static String requireName(String name) {
    String trimmed = name.trim();
    if (trimmed.isEmpty()) {
      throw new InvalidLabelException("Labelname darf nicht leer sein");
    }
    return trimmed;
  }
}
