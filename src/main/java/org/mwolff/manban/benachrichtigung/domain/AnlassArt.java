package org.mwolff.manban.benachrichtigung.domain;

import java.util.Optional;
import org.mwolff.manban.common.KitNamen.Sache;

/**
 * Worüber eine Mail informiert (Issue #2073, Plan #2070 E4).
 *
 * <p>Die Namen sind zugleich die Werte der Spalte {@code benachrichtigung_anlass.art}.
 */
public enum AnlassArt {
  LAUF_WARTET,
  STOERUNG,
  UEBERSICHT,
  KARTE_FRAGE,
  KARTE_GESCHUETZT;

  /**
   * Art zur Sache eines Kit-Labels. Über die Sache gelten beide Namen eines Label-Paars als ein
   * Anlass; eine eigene Namensliste gibt es nicht.
   *
   * @return die Art, leer für jede Sache, die keinen Anlass bezeichnet
   */
  public static Optional<AnlassArt> ausSache(Sache sache) {
    return switch (sache) {
      case KLAEREN -> Optional.of(KARTE_FRAGE);
      case LAUF_WARTET -> Optional.of(LAUF_WARTET);
      case GESCHUETZT -> Optional.of(KARTE_GESCHUETZT);
      default -> Optional.empty();
    };
  }
}
