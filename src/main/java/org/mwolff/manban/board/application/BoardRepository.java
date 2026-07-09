package org.mwolff.manban.board.application;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.board.domain.Board;

/** Ausgehender Port für die Persistenz von Boards. */
public interface BoardRepository {

    Board save(Board board);

    Optional<Board> findById(long id);

    List<Board> findByProjectId(long projectId);

    void deleteById(long id);
}
