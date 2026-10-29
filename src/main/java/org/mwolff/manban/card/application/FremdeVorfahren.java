package org.mwolff.manban.card.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.mwolff.manban.card.domain.Card;

/**
 * Nummern der Vorfahren, die nicht auf einem Board liegen — ein Sammelzugriff statt einer Abfrage
 * je Kante (Issue #1982, Plan #1980). Gemeinsam genutzt vom Baum des Vorhabens und vom Zweig im
 * Karten-Detail, statt dort kopiert zu werden.
 *
 * <p>Bezugsmenge ist bewusst das <b>Board</b> und nicht die jeweils übergebene Teilmenge: Nur so
 * behält eine board-fremde Herkunft im Baum ihre Nummer, während board-interne Nicht-Mitglieder
 * ohne Nummer extern bleiben.
 *
 * <p><b>Nur aus dem eigenen Projekt</b> (Plan #1980, E4): {@code findByIds} filtert nicht nach
 * Projekt. Ein Vorfahr in einem anderen Projekt bekommt keine Nummer — der Betrachter muss dort
 * nicht Mitglied sein, und der Baum nennt ihn dann wie jede unbekannte Herkunft extern ohne Nummer.
 */
final class FremdeVorfahren {

  private FremdeVorfahren() {}

  /**
   * Nummern der board-fremden Vorfahren aus dem Projekt des Boards.
   *
   * @param projectId das Projekt des Boards; nur Vorfahren daraus liefern ihre Nummer
   * @param boardCards alle Karten des Boards
   * @return Karten-ID des board-fremden Vorfahren → seine Nummer
   */
  static Map<Long, Integer> nummern(CardRepository cards, long projectId, List<Card> boardCards) {
    Set<Long> imBoard = boardCards.stream().map(Card::requireId).collect(Collectors.toSet());
    Set<Long> fremde =
        boardCards.stream()
            .map(Card::derivedFromCardId)
            .filter(Objects::nonNull)
            .filter(id -> !imBoard.contains(id))
            .collect(Collectors.toSet());
    Map<Long, Integer> nummern = new HashMap<>();
    if (!fremde.isEmpty()) {
      for (Card vorfahr : cards.findByIds(fremde)) {
        if (vorfahr.projectId() == projectId) {
          nummern.put(vorfahr.requireId(), vorfahr.number());
        }
      }
    }
    return nummern;
  }
}
