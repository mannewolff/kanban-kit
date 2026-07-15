package org.mwolff.manban.card.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.card.application.LabelRepository;
import org.mwolff.manban.card.domain.Label;
import org.springframework.stereotype.Component;

/** Adapter des {@link LabelRepository}-Ports auf Spring Data JPA. */
@Component
class LabelRepositoryAdapter implements LabelRepository {

  private final LabelJpaRepository jpa;

  LabelRepositoryAdapter(LabelJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Label save(Label label) {
    return toDomain(jpa.save(new LabelEntity(label)));
  }

  @Override
  public Optional<Label> findById(long id) {
    return jpa.findById(id).map(LabelRepositoryAdapter::toDomain);
  }

  @Override
  public List<Label> findByBoardId(long boardId) {
    return jpa.findByBoardIdOrderByName(boardId).stream()
        .map(LabelRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean existsByBoardIdAndName(long boardId, String name) {
    return jpa.existsByBoardIdAndName(boardId, name);
  }

  @Override
  public void deleteById(long id) {
    jpa.deleteById(id);
  }

  private static Label toDomain(LabelEntity e) {
    return new Label(e.getId(), e.getBoardId(), e.getName(), e.getColor());
  }
}
