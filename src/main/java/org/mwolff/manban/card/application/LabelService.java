package org.mwolff.manban.card.application;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mwolff.manban.board.application.BoardService;
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
  private final CardLabelRepository cardLabels;
  private final BoardService boardService;
  private final PermissionChecker permissions;

  public LabelService(
      LabelRepository labels,
      CardLabelRepository cardLabels,
      BoardService boardService,
      PermissionChecker permissions) {
    this.labels = labels;
    this.cardLabels = cardLabels;
    this.boardService = boardService;
    this.permissions = permissions;
  }

  /**
   * Baut je Karte die Liste der Label-<em>Namen</em> aus genau zwei Batch-Abfragen auf — {@link
   * LabelRepository#findByBoardId} (Namen) und {@link CardLabelRepository#findByCardIds}
   * (Zuordnung) — unabhängig von der Kartenzahl (kein N+1). Die Reihenfolge je Karte folgt der
   * Board-Definitionsreihenfolge, nicht der Zuordnungsreihenfolge; jede übergebene Karten-ID erhält
   * einen Eintrag (leere Liste, wenn ihr kein Label zugeordnet ist).
   *
   * <p>Ohne eigene Rechteprüfung: die Karten-IDs stammen beim einzigen Aufrufer aus einer bereits
   * rechtegeprüften Board-Abfrage ({@link CardService#listBoardItems}).
   */
  @Transactional(readOnly = true)
  public Map<Long, List<String>> namesByCard(long boardId, Collection<Long> cardIds) {
    List<Label> boardLabels = labels.findByBoardId(boardId);
    Map<Long, List<Long>> labelIdsByCard = cardLabels.findByCardIds(cardIds);
    Map<Long, List<String>> result = new LinkedHashMap<>();
    for (Long cardId : cardIds) {
      Set<Long> assigned = new HashSet<>(labelIdsByCard.getOrDefault(cardId, List.of()));
      result.put(
          cardId,
          boardLabels.stream()
              .filter(l -> assigned.contains(l.requireId()))
              .map(Label::name)
              .toList());
    }
    return result;
  }

  @Transactional(readOnly = true)
  public List<Label> list(long userId, long boardId) {
    permissions.requireMembership(userId, boardService.requireProjectId(boardId));
    return labels.findByBoardId(boardId);
  }

  @Transactional
  public Label create(long userId, long boardId, String name, String color) {
    permissions.require(userId, boardService.requireProjectId(boardId), Permission.BOARD_UPDATE);
    String trimmed = requireName(name);
    if (labels.existsByBoardIdAndName(boardId, trimmed)) {
      throw new InvalidLabelException("Label existiert bereits: " + trimmed);
    }
    return labels.save(new Label(null, boardId, trimmed, color));
  }

  @Transactional
  public Label update(long userId, long labelId, String name, String color) {
    Label label = labels.findById(labelId).orElseThrow(LabelNotFoundException::new);
    permissions.require(
        userId, boardService.requireProjectId(label.boardId()), Permission.BOARD_UPDATE);
    String trimmed = requireName(name);
    if (!trimmed.equals(label.name()) && labels.existsByBoardIdAndName(label.boardId(), trimmed)) {
      throw new InvalidLabelException("Label existiert bereits: " + trimmed);
    }
    return labels.save(label.withContent(trimmed, color));
  }

  @Transactional
  public void delete(long userId, long labelId) {
    Label label = labels.findById(labelId).orElseThrow(LabelNotFoundException::new);
    permissions.require(
        userId, boardService.requireProjectId(label.boardId()), Permission.BOARD_UPDATE);
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
