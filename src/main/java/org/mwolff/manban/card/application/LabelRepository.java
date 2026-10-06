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

  /**
   * Legt das Label am Board an, wenn das Board diesen Namen noch nicht trägt (Issue #1485). Atomar
   * über die Eindeutigkeitsbedingung {@code uq_label_board_name}: Auch zwei gleichzeitige Aufrufe
   * legen es nur einmal an, und der unterlegene bricht seine Transaktion nicht ab. Ein neues Label
   * zählt nicht auf der Kachel eines Vorhabens mit.
   *
   * @return {@code true}, wenn das Label neu angelegt wurde; {@code false}, wenn es schon bestand
   */
  boolean insertIfAbsent(long boardId, String name, String color);

  void deleteById(long id);
}
