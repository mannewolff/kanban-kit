package org.mwolff.manban.comment.domain;

import java.util.Optional;
import org.mwolff.manban.common.KitMeldungen;

/**
 * Sprache eines Kopfs einer Rückfrage oder Antwort (Issue #2210, Plan #2206 E9). Gelesen werden
 * beide Fassungen; geschrieben wird die Antwort in der Sprache der Rückfrage.
 */
public enum Kopfsprache {
  DEUTSCH(0),
  ENGLISCH(1);

  /** Stelle des Namens in {@link KitMeldungen.Zeile#namen()}. */
  private final int stelle;

  Kopfsprache(int stelle) {
    this.stelle = stelle;
  }

  /**
   * Die Sprache, in der diese Zeile der Kopf {@code zeile} ist; leer, wenn sie es nicht ist. Der
   * Kopf steht allein auf der Zeile, führender und folgender Leerraum und die Schreibung zählen
   * nicht.
   */
  static Optional<Kopfsprache> vonKopf(KitMeldungen.Zeile zeile, String kopfzeile) {
    String roh = kopfzeile.strip();
    for (Kopfsprache sprache : values()) {
      if (roh.equalsIgnoreCase(sprache.kopf(zeile))) {
        return Optional.of(sprache);
      }
    }
    return Optional.empty();
  }

  /** Der Kopf {@code zeile} in dieser Sprache. */
  String kopf(KitMeldungen.Zeile zeile) {
    return zeile.namen().get(stelle);
  }
}
