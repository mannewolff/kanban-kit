package org.mwolff.manban.board.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.board.application.BoardColumnRepository;
import org.mwolff.manban.board.domain.BoardColumn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Adapter des {@link BoardColumnRepository}-Ports auf Spring Data JPA. */
@Component
class BoardColumnRepositoryAdapter implements BoardColumnRepository {

  private final BoardColumnJpaRepository jpa;
  private final JdbcTemplate jdbc;

  BoardColumnRepositoryAdapter(BoardColumnJpaRepository jpa, JdbcTemplate jdbc) {
    this.jpa = jpa;
    this.jdbc = jdbc;
  }

  @Override
  public BoardColumn save(BoardColumn column) {
    return toDomain(jpa.save(toEntity(column)));
  }

  @Override
  public Optional<BoardColumn> findById(long id) {
    return jpa.findById(id).map(BoardColumnRepositoryAdapter::toDomain);
  }

  @Override
  public List<BoardColumn> findByBoardId(long boardId) {
    return jpa.findByBoardIdOrderByPosition(boardId).stream()
        .map(BoardColumnRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public void deleteById(long id) {
    jpa.deleteById(id);
  }

  /**
   * Zwei-Phasen-Reindex: erst alle Positionen weit nach oben verschieben (kollisionsfrei gegen den
   * Unique-Constraint), dann die Zielpositionen 0..n-1 setzen.
   */
  @Override
  public void reorder(long boardId, List<Long> orderedColumnIds) {
    jdbc.update("UPDATE board_column SET position = position + 100000 WHERE board_id = ?", boardId);
    for (int i = 0; i < orderedColumnIds.size(); i++) {
      jdbc.update(
          "UPDATE board_column SET position = ? WHERE id = ? AND board_id = ?",
          i,
          orderedColumnIds.get(i),
          boardId);
    }
  }

  private static BoardColumnEntity toEntity(BoardColumn c) {
    return new BoardColumnEntity(c.id(), c.boardId(), c.name(), c.position(), c.wipLimit());
  }

  private static BoardColumn toDomain(BoardColumnEntity e) {
    return new BoardColumn(
        e.getId(), e.getBoardId(), e.getName(), e.getPosition(), e.getWipLimit());
  }
}
