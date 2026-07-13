package org.mwolff.manban.board.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.board.application.BoardRepository;
import org.mwolff.manban.board.domain.Board;
import org.springframework.stereotype.Component;

/** Adapter des {@link BoardRepository}-Ports auf Spring Data JPA. */
@Component
class BoardRepositoryAdapter implements BoardRepository {

  private final BoardJpaRepository jpa;

  BoardRepositoryAdapter(BoardJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Board save(Board board) {
    return toDomain(jpa.save(toEntity(board)));
  }

  @Override
  public Optional<Board> findById(long id) {
    return jpa.findByIdAndArchivedAtIsNull(id).map(BoardRepositoryAdapter::toDomain);
  }

  @Override
  public Optional<Board> findByIdIncludingArchived(long id) {
    return jpa.findById(id).map(BoardRepositoryAdapter::toDomain);
  }

  @Override
  public List<Board> findByProjectId(long projectId) {
    return jpa.findByProjectIdAndArchivedAtIsNullOrderByCreatedAt(projectId).stream()
        .map(BoardRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<Board> findArchivedByProjectId(long projectId) {
    return jpa.findByProjectIdAndArchivedAtIsNotNullOrderByCreatedAt(projectId).stream()
        .map(BoardRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public void deleteById(long id) {
    jpa.deleteById(id);
  }

  private static BoardEntity toEntity(Board b) {
    return new BoardEntity(b.id(), b.projectId(), b.name(), b.createdAt(), b.archivedAt());
  }

  private static Board toDomain(BoardEntity e) {
    return new Board(e.getId(), e.getProjectId(), e.getName(), e.getCreatedAt(), e.getArchivedAt());
  }
}
