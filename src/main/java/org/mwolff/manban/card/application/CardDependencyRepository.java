package org.mwolff.manban.card.application;

import java.util.List;

/** Ausgehender Port für die Abhängigkeiten einer Karte (board-scoped Nummern). */
public interface CardDependencyRepository {

    /** Ersetzt alle Abhängigkeiten der Karte durch die übergebenen Kartennummern. */
    void replaceDependencies(long cardId, List<Integer> dependsOnNumbers);

    List<Integer> findByCardId(long cardId);

    void deleteByCardId(long cardId);
}
