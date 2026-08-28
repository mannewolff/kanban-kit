package org.mwolff.manban.card.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Ausgehender Port für die Abhängigkeiten einer Karte.
 *
 * <p>Die gespeicherten Werte sind <strong>projektweite</strong> Kartennummern. Ein früherer
 * Kommentar an dieser Stelle sprach von board-weiten Nummern; das war seit der Migration <b>V19</b>
 * falsch, die die Eindeutigkeit auf {@code uq_card_number (project_id, number)} umgestellt hat.
 */
public interface CardDependencyRepository {

  /** Ersetzt alle Abhängigkeiten der Karte durch die übergebenen Kartennummern. */
  void replaceDependencies(long cardId, List<Integer> dependsOnNumbers);

  List<Integer> findByCardId(long cardId);

  /**
   * Abhängigkeiten mehrerer Karten in <strong>einem</strong> Zug, als Zuordnung Karten-ID →
   * Nummern.
   *
   * <p>Der Sammelzugriff existiert, weil der Herkunftsbaum (Issue #609) je Zeile Kanten braucht.
   * Mit {@link #findByCardId(long)} wäre er auf der Kantenseite N+1, auch wenn die Kartenseite mit
   * einer Abfrage auskommt — genau das Loch, das der Bestand für {@code view(...)} selbst
   * dokumentiert.
   *
   * <p>Karten ohne Abhängigkeiten fehlen in der Antwort; Aufrufer behandeln das wie eine leere
   * Liste. Ein Eintrag mit leerer Liste wäre eine zweite Darstellung desselben Zustands.
   */
  Map<Long, List<Integer>> findByCardIds(Collection<Long> cardIds);

  void deleteByCardId(long cardId);
}
