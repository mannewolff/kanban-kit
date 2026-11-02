package org.mwolff.manban.card.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Das Kostenvorhaben je Karte (Issue #2000, Plan #1990 E1, fachlich #1954 AK 1 und 12): Jede Karte
 * zählt bei genau einem Vorhaben, damit die Kosten je Vorhaben zusammen nie mehr ergeben als die
 * Kosten der Karten.
 *
 * <p>Die Regel: Unmittelbar geht vor geerbt. Ist die Karte selbst über {@code parentId} einem
 * Vorhaben zugeordnet, ist es dieses; sonst das erste Vorhaben, das der Weg über {@code
 * derivedFromCardId} nach oben erreicht. Der Weg geht durch archivierte Karten und durch Vorhaben
 * hindurch — wie die Zugehörigkeit, die ein Vorhaben auf der Herkunftskette nicht als Wurzel kennt.
 *
 * <p>Ein Kandidat zählt nur, wenn die Zugehörigkeit die Karte zu ihm rechnet. So bleibt die
 * Kostenzuordnung eine Auswahl aus der Zugehörigkeit und kein zweiter Begriff von ihr. Rein, ohne
 * Datenbank; der Weg führt eine Menge besuchter IDs, weil Herkunftsringe über die API erreichbar
 * sind (Issue #607).
 */
public final class Kostenzuordnung {

  private Kostenzuordnung() {}

  /**
   * Das Kostenvorhaben jeder Karte eines Boards.
   *
   * @param boardCards Karten eines Boards samt Archiv, ohne Papierkorb
   * @param zugehoerigeMitArchiv je Vorhaben-ID die Zugehörigen samt Archiv ({@code
   *     EpicMembership.computeMitArchiv})
   * @return je Kartennummer ihr Vorhaben; Karten ohne Vorhaben und Vorhaben selbst fehlen
   */
  public static Map<Integer, Card> ermittle(
      List<Card> boardCards, Map<Long, Set<Card>> zugehoerigeMitArchiv) {
    Map<Long, Card> nachId = new HashMap<>();
    for (Card c : boardCards) {
      nachId.put(c.requireId(), c);
    }
    Map<Integer, Card> ergebnis = new HashMap<>();
    for (Card karte : boardCards) {
      @Nullable Card vorhaben = kostenvorhaben(karte, nachId, zugehoerigeMitArchiv);
      if (vorhaben != null) {
        ergebnis.put(karte.number(), vorhaben);
      }
    }
    return ergebnis;
  }

  private static @Nullable Card kostenvorhaben(
      Card karte, Map<Long, Card> nachId, Map<Long, Set<Card>> zugehoerige) {
    Set<Long> besucht = new HashSet<>();
    @Nullable Card glied = karte;
    while (glied != null && besucht.add(glied.requireId())) {
      @Nullable Card kandidat = nachId.get(glied.parentId());
      if (kandidat != null
          && zugehoerige.getOrDefault(kandidat.requireId(), Set.of()).contains(karte)) {
        return kandidat;
      }
      glied = nachId.get(glied.derivedFromCardId());
    }
    return null;
  }
}
