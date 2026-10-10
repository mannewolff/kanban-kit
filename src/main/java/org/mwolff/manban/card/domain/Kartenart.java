package org.mwolff.manban.card.domain;

import org.mwolff.manban.common.KitNamen;

/**
 * Die Art einer Karte, wie die Werkzeuge sie suchen (Plan #1574, E1): ein Vorhaben nach seinem Typ,
 * jede übrige Karte nach dem Präfix ihres Titels, ohne Präfix ein Arbeitspaket.
 *
 * <p>Bewusst getrennt von {@link Arbeitspaket#istArbeitspaket}: Jene Statusregel zählt {@code
 * [Task]} und {@code [Mensch]} als Arbeitspaket, weil beide einen Status tragen. Für die Suche sind
 * es eigene Arten. Die Statusregel bleibt unberührt.
 *
 * <p>Modulintern wie alles in {@code card.domain}: Über die Fassade geht die Art als Konstantenname
 * ({@link #name()}).
 */
public enum Kartenart {
  FACHLICH,
  PLAN,
  IDEE,
  TASK,
  MENSCH,
  ARBEITSPAKET,
  VORHABEN;

  /**
   * Die Art einer Karte dieses Typs mit diesem Titel. Das Titel-Präfix liest {@link
   * KitNamen#artAusTitel} in beiden Sprachen (Issue #1587, Plan #1582 E2); jede {@link
   * KitNamen.Art} heißt wie ihre Kartenart.
   */
  public static Kartenart von(CardType type, String titel) {
    if (type == CardType.EPIC) {
      return VORHABEN;
    }
    return KitNamen.artAusTitel(titel).map(art -> valueOf(art.name())).orElse(ARBEITSPAKET);
  }
}
