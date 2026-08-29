package org.mwolff.manban.card.application;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;

/**
 * Berechnet die Zugehörigkeit zu einem Vorhaben (Issue #632): Wer einem Vorhaben über {@code
 * parentId} zugeordnet ist, bringt seinen Nachfahrenbaum über {@code derivedFromCardId} mit.
 *
 * <p>Eigene Klasse und nicht eine Methode in {@code CardService}: Der Graph-Teil ist reine Rechnung
 * ohne Ports, und er ist der einzige Teil, den man ohne Datenbank vollständig durchtesten kann.
 * Dieselbe Trennung wie bei {@link DerivationTree} und {@link DerivedFrom}.
 *
 * <p><b>Die Filter wirken auf die Zugehörigkeit, nicht auf die Kantenverfolgung.</b> Eine Karte,
 * die selbst nicht zählt — archiviert, im Ideen-Speicher oder selbst ein Vorhaben —, unterbricht
 * die Kette nicht: Ihre Nachfahren bleiben zugehörig. Andernfalls machte ein archiviertes
 * Zwischendokument seine Arbeitspakete unsichtbar, obwohl an ihnen noch gearbeitet wird.
 *
 * <p><b>Der Abstieg führt eine Menge besuchter IDs</b>, obwohl der Schreibpfad Zyklen ablehnt.
 * {@link DerivationTree} darf darauf verzichten, weil es ausschliesslich von herkunftslosen Wurzeln
 * absteigt und einen Ring konstruktiv nie erreicht. Hier kommen die Wurzeln dagegen aus {@code
 * parentId}: Ist ein Ringmitglied einem Vorhaben zugeordnet, betritt der Abstieg den Ring und liefe
 * ohne die Menge endlos. {@link DerivedFrom} hält fest, dass Selbstverweis und Zyklus seit Issue
 * #607 über die API erreichbar sind.
 */
final class EpicMembership {

  private EpicMembership() {}

  /**
   * Bestimmt je Vorhaben die zugehörigen Karten — ohne weitere Abfragen, aus einer bereits
   * geladenen Kartenmenge.
   *
   * @param boardCards Karten dieses Boards; die Board-Bindung ist damit der Zuschnitt (Plan #631,
   *     E4). Eine Herkunftskante auf ein anderes Board endet still, weil ihr Ziel hier fehlt
   * @return je Vorhaben-ID die zugehörigen Karten. <b>Jedes</b> Vorhaben des Boards ist als
   *     Schlüssel enthalten, auch mit leerer Menge. Karten statt IDs, weil der Aufrufer für die
   *     Done-Zählung die Spalten-Zuordnung braucht
   */
  static Map<Long, Set<Card>> compute(List<Card> boardCards) {
    // Kanten über den Vorfahren selbst statt über dessen ID: Fehlt er auf diesem Board (oder fehlt
    // die Herkunft ganz), liefert das Nachschlagen null und die Kante entsteht nicht.
    Map<Long, Card> byId = new HashMap<>();
    for (Card c : boardCards) {
      byId.put(c.requireId(), c);
    }
    Map<Long, List<Card>> kinder = new HashMap<>();
    for (Card c : boardCards) {
      Card vorfahr = byId.get(c.derivedFromCardId());
      if (vorfahr != null) {
        kinder.computeIfAbsent(vorfahr.requireId(), k -> new ArrayList<>()).add(c);
      }
    }

    Map<Long, Set<Card>> ergebnis = new HashMap<>();
    for (Card epic : boardCards) {
      if (epic.type() == CardType.EPIC) {
        ergebnis.put(epic.requireId(), sammle(epic.requireId(), boardCards, kinder));
      }
    }
    return ergebnis;
  }

  /** Steigt von den direkt zugeordneten Wurzeln ab und sammelt ein, was zählt. */
  private static Set<Card> sammle(
      long epicId, List<Card> boardCards, Map<Long, List<Card>> kinder) {
    Deque<Card> offen = new ArrayDeque<>();
    for (Card c : boardCards) {
      if (Long.valueOf(epicId).equals(c.parentId())) {
        offen.add(c);
      }
    }

    // Je Vorhaben eine eigene Menge: Eine Karte darf zu mehreren Vorhaben gehören (Plan #631, E10),
    // eine gemeinsame Besuchtenmenge würde sie dem zweiten Vorhaben stillschweigend vorenthalten.
    Set<Long> besucht = new HashSet<>();
    Set<Card> zugehoerig = new LinkedHashSet<>();
    while (!offen.isEmpty()) {
      Card c = offen.removeFirst();
      if (!besucht.add(c.requireId())) {
        continue;
      }
      if (zaehlt(c)) {
        zugehoerig.add(c);
      }
      offen.addAll(kinder.getOrDefault(c.requireId(), List.of()));
    }
    return zugehoerig;
  }

  /**
   * Ob eine erreichte Karte in die Menge gehört. Wer hier ausscheidet, wird trotzdem durchlaufen —
   * siehe die Grundregel im Klassen-Javadoc.
   */
  private static boolean zaehlt(Card c) {
    return c.type() != CardType.EPIC && !c.archived() && !c.ideaStored();
  }
}
