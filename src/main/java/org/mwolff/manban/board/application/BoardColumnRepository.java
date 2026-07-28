package org.mwolff.manban.board.application;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.board.domain.BoardColumn;

/** Ausgehender Port für die Persistenz von Board-Spalten. */
public interface BoardColumnRepository {

  BoardColumn save(BoardColumn column);

  Optional<BoardColumn> findById(long id);

  /** Spalten eines Boards, aufsteigend nach Position. */
  List<BoardColumn> findByBoardId(long boardId);

  void deleteById(long id);

  /**
   * Sperrt die Spaltenordnung des Boards bis zum Transaktionsende — aufzurufen, <em>bevor</em> die
   * bestehenden Spalten gelesen werden, aus denen die neue Ordnung entsteht.
   *
   * <p><strong>Warum (Issue #499):</strong> Sowohl das Anhängen einer Spalte ({@code max(position)
   * + 1}) als auch das Umsortieren berechnen die neuen Positionen aus dem gelesenen Bestand. Unter
   * READ COMMITTED sahen zwei gleichzeitige Aufrufe denselben Bestand: Zwei Anlagen rechneten
   * dieselbe Position aus und liefen in {@code uq_board_column_position}; ein Umsortieren neben
   * einer Anlage vergab lückenhafte Positionen, weil die neue Spalte in der gelesenen Ordnung
   * fehlte.
   *
   * <p>Serialisiert wird über die <em>Board-Zeile</em>: Sie ist der Träger des Positions-Namespace
   * ({@code uq_board_column_position (board_id, position)}), und beim Anlegen gibt es die
   * Spaltenzeile noch nicht, die man sonst sperren würde. Verschiedene Boards bremsen sich damit
   * nicht gegenseitig aus.
   *
   * <p>Sperrordnung: Board vor Karten-Spaltensperren. Kein Use-Case nimmt beides, deshalb kann
   * keine Überkreuzung entstehen.
   */
  void lockColumnOrder(long boardId);

  /**
   * Weist den Spalten des Boards neue, lückenlose Positionen in der Reihenfolge der übergebenen IDs
   * zu (kollisionsfrei trotz Unique-Constraint).
   */
  void reorder(long boardId, List<Long> orderedColumnIds);
}
