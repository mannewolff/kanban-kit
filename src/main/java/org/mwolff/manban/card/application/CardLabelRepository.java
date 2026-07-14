package org.mwolff.manban.card.application;

import java.util.List;

/** Ausgehender Port für die Label-Zuordnung einer Karte. */
public interface CardLabelRepository {

  /** Ersetzt die Labels der Karte vollständig durch die übergebenen Label-IDs. */
  void replaceLabels(long cardId, List<Long> labelIds);

  /** Label-IDs der Karte, aufsteigend. */
  List<Long> findByCardId(long cardId);
}
