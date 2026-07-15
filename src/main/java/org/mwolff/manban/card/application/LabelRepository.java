package org.mwolff.manban.card.application;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.card.domain.Label;

/** Ausgehender Port für die Persistenz von Labels. */
public interface LabelRepository {

  Label save(Label label);

  Optional<Label> findById(long id);

  /** Labels eines Boards, aufsteigend nach Name. */
  List<Label> findByBoardId(long boardId);

  boolean existsByBoardIdAndName(long boardId, String name);

  void deleteById(long id);
}
