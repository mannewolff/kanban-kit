package org.mwolff.manban.fremdboard.domain;

import java.util.List;
import java.util.Set;

/**
 * Die Kommentare an einer Karte im fremden System (Issue #2158), getrennt nach ihrer
 * Vertraulichkeit (Issue #2200): Eine interne Notiz wird nicht gespiegelt, und eine schon
 * gespiegelte entfernt der Abgleich der Karte.
 *
 * @param sichtbar die Kommentare, die gespiegelt werden dürfen, ältester zuerst, als
 *     unveränderliche Kopie
 * @param vertraulich Kennungen der internen Notizen, als unveränderliche Kopie; nur die Kennung
 *     verlässt die Quelle, kein Text
 */
public record FremdNotizen(List<FremdNotiz> sichtbar, Set<String> vertraulich) {

  /** Kopiert beide Sammlungen, damit die Notizen nach dem Lesen unverändert bleiben. */
  public FremdNotizen {
    sichtbar = List.copyOf(sichtbar);
    vertraulich = Set.copyOf(vertraulich);
  }
}
