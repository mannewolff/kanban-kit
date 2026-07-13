package org.mwolff.manban.card.application;

import java.util.List;

/** Ausgehender Port für die Zuständigen (Assignees) einer Karte. */
public interface CardAssigneeRepository {

  /** Ersetzt die Zuständigen der Karte vollständig durch die übergebenen Benutzer-IDs. */
  void replaceAssignees(long cardId, List<Long> userIds);

  /** Benutzer-IDs der Zuständigen der Karte, aufsteigend. */
  List<Long> findByCardId(long cardId);

  /** Entfernt alle Zuständigen der Karte. */
  void deleteByCardId(long cardId);
}
