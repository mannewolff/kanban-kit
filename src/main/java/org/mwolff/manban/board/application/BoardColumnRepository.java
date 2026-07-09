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
     * Weist den Spalten des Boards neue, lückenlose Positionen in der Reihenfolge
     * der übergebenen IDs zu (kollisionsfrei trotz Unique-Constraint).
     */
    void reorder(long boardId, List<Long> orderedColumnIds);
}
