package org.mwolff.manban.card.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.domain.Card;
import org.springframework.stereotype.Service;

/**
 * Die Abhängigkeiten einer Karte: lesen, prüfen, ersetzen (Issue #1389, Plan #1387 E11).
 *
 * <p>Der einzige Halter von {@link CardDependencyRepository} im Modul — ein Thema an genau einem
 * Ort. Modulintern wie {@link KartenZuordnung}: Die Klasse steht nicht auf der Fassaden-Whitelist
 * der ArchUnit-Regel {@code CARD_APPLICATION_IST_AUF_FASSADE_BEGRENZT}.
 *
 * <p>Ohne {@code @Transactional}: Jede Methode läuft in der Transaktion des aufrufenden
 * Use-Case-Verfahrens.
 */
@Service
public final class KartenAbhaengigkeiten {

  private final CardDependencyRepository dependencies;
  private final CardRepository cards;

  public KartenAbhaengigkeiten(CardDependencyRepository dependencies, CardRepository cards) {
    this.dependencies = dependencies;
    this.cards = cards;
  }

  /** Abhängigkeits-Nummern einer Karte. */
  public List<Integer> abhaengigkeitenVon(long cardId) {
    return dependencies.findByCardId(cardId);
  }

  /**
   * Abhängigkeiten mehrerer Karten in einem Sammelzugriff. Karten ohne Abhängigkeiten fehlen in der
   * Map — der Vertrag von {@link CardDependencyRepository#findByCardIds(Collection)}.
   */
  public Map<Long, List<Integer>> abhaengigkeitenJeKarte(Collection<Long> cardIds) {
    return dependencies.findByCardIds(cardIds);
  }

  /**
   * Prüft und ersetzt die Abhängigkeiten einer Karte — der Weg der Oberfläche. {@code null} oder
   * leer entfernt alle; Duplikate fallen heraus. Abgelehnt werden der Selbstverweis und jede
   * Nummer, die im Projekt keine Karte trägt.
   */
  public void ersetze(Card card, @Nullable List<Integer> dependsOn) {
    if (dependsOn == null || dependsOn.isEmpty()) {
      dependencies.replaceDependencies(card.requireId(), List.of());
      return;
    }
    List<Integer> distinct = dependsOn.stream().distinct().toList();
    // Querverweise werden projektweit aufgelöst: eine #N-Abhängigkeit darf auf jede Karte desselben
    // Projekts zeigen (board-übergreifend), nicht nur auf dasselbe Board.
    List<Integer> projectNumbers =
        cards.findByProjectId(card.projectId()).stream().map(Card::number).toList();
    int eigeneNummer = card.number();
    for (Integer dep : distinct) {
      if (dep == eigeneNummer) {
        throw new InvalidDependencyException("Karte kann nicht von sich selbst abhängen");
      }
      if (!projectNumbers.contains(dep)) {
        throw new InvalidDependencyException("Unbekannte Kartennummer: " + dep);
      }
    }
    dependencies.replaceDependencies(card.requireId(), distinct);
  }

  /**
   * Ersetzt die Abhängigkeiten einer Karte, <strong>ohne</strong> die Zielnummern auf Existenz zu
   * prüfen (Issue #566) — der Weg des Imports aus einem anderen Tracker, siehe {@link
   * CardService#replaceDependenciesFromIngest}. Geteilt mit {@link #ersetze} bleibt die
   * Selbstverweis-Prüfung — sie hängt nicht am Wissen über andere Karten.
   */
  public void ersetzeOhneExistenzpruefung(Card card, @Nullable List<Integer> dependsOn) {
    // Kein Sonderfall für „leer": Die Schleife läuft dann einfach nicht, und replaceDependencies
    // bekommt eine leere Liste — dasselbe Ergebnis, ein Zweig weniger.
    List<Integer> distinct = dependsOn == null ? List.of() : dependsOn.stream().distinct().toList();
    int eigeneNummer = card.number();
    for (Integer dep : distinct) {
      if (dep == eigeneNummer) {
        throw new InvalidDependencyException("Karte kann nicht von sich selbst abhängen");
      }
    }
    dependencies.replaceDependencies(card.requireId(), distinct);
  }

  /**
   * Entfernt alle Abhängigkeiten einer Karte — beim Projektwechsel und beim endgültigen Löschen.
   */
  public void entferne(long cardId) {
    dependencies.deleteByCardId(cardId);
  }
}
