package org.mwolff.manban.entscheidungskette.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Karte;

/** Das Glied Idee aus der Zeile {@code Ergebnis:} einer Idee (Kriterium 7). */
final class Ideensuche {

  private Ideensuche() {}

  /**
   * Die Idee, deren {@code Ergebnis:} auf das erste der Ziele verweist; bei mehreren die mit der
   * kleinsten ID. Ohne sie ist die Idee nie „nicht vorgesehen“, sondern „nicht erfasst“.
   */
  static Glied glied(List<Karte> ideen, List<Integer> ziele, Kartenglieder glieder) {
    List<Karte> geordnet = ideen.stream().sorted(Comparator.comparingLong(Karte::id)).toList();
    return ziele.stream()
        .flatMap(ziel -> geordnet.stream().flatMap(i -> mitErgebnis(i, ziel).stream()))
        .findFirst()
        .map(t -> glieder.anlage(Gliedart.IDEE, t.idee(), t.ergebnis()))
        .orElseGet(
            () ->
                Glied.ohneAngaben(Gliedart.IDEE, Zustand.NICHT_ERFASST, Verweis.KEINER, Set.of()));
  }

  private static Optional<Treffer> mitErgebnis(Karte idee, int ziel) {
    return KitMeldungLeser.ideeErgebnis(Objects.requireNonNullElse(idee.beschreibung(), ""))
        .filter(e -> Dokumentzeilen.ergebnisNummer(e).filter(n -> n == ziel).isPresent())
        .map(e -> new Treffer(idee, e));
  }

  private record Treffer(Karte idee, String ergebnis) {}
}
