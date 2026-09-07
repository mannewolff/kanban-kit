package org.mwolff.manban.card.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Ausgehender Port für die Zuständigen (Assignees) einer Karte. */
public interface CardAssigneeRepository {

  /** Ersetzt die Zuständigen der Karte vollständig durch die übergebenen Benutzer-IDs. */
  void replaceAssignees(long cardId, List<Long> userIds);

  /** Benutzer-IDs der Zuständigen der Karte, aufsteigend. */
  List<Long> findByCardId(long cardId);

  /**
   * Zuständige mehrerer Karten in <strong>einem</strong> Zug, als Zuordnung Karten-ID →
   * Benutzer-IDs.
   *
   * <p>Der Sammelzugriff ist das Gegenstück zu {@link
   * CardDependencyRepository#findByCardIds(Collection)} und {@link
   * CardLabelRepository#findByCardIds(Collection)}: Eine Kartenliste braucht die Zuständigen aller
   * Karten, und mit {@link #findByCardId(long)} wäre das eine Abfrage je Karte.
   *
   * <p>Die leere Eingabe liefert eine leere Map ohne Abfrage. Karten ohne Zuständige fehlen in der
   * Antwort; Aufrufer behandeln das wie eine leere Liste. Ein Eintrag mit leerer Liste wäre eine
   * zweite Darstellung desselben Zustands.
   *
   * <p>Je Karte sind die Benutzer-IDs aufsteigend sortiert — dieselbe Reihenfolge wie bei {@link
   * #findByCardId(long)}.
   */
  Map<Long, List<Long>> findByCardIds(Collection<Long> cardIds);

  /** Entfernt alle Zuständigen der Karte. */
  void deleteByCardId(long cardId);
}
