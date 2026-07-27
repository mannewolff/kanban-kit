package org.mwolff.manban.card.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Ausgehender Port für die Label-Zuordnung einer Karte. */
public interface CardLabelRepository {

  /** Ersetzt die Labels der Karte vollständig durch die übergebenen Label-IDs. */
  void replaceLabels(long cardId, List<Long> labelIds);

  /** Label-IDs der Karte, aufsteigend. */
  List<Long> findByCardId(long cardId);

  /**
   * Label-IDs mehrerer Karten in einer einzigen Batch-Abfrage ({@code IN}-Clause), je Karte
   * aufsteigend. Karten ohne Labels fehlen im Ergebnis; leere Eingabe liefert eine leere Map.
   */
  Map<Long, List<Long>> findByCardIds(Collection<Long> cardIds);
}
