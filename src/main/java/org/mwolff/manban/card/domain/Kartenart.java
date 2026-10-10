package org.mwolff.manban.card.domain;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  /** Präfix am Titelanfang, führende Leerzeichen erlaubt — wortgleich zu den Präfixen des Kits. */
  private static final Pattern PRAEFIX =
      Pattern.compile("^\\s*\\[(fachlich|plan|idee|task|mensch)\\]", Pattern.CASE_INSENSITIVE);

  /** Die Art einer Karte dieses Typs mit diesem Titel. */
  public static Kartenart von(CardType type, String titel) {
    if (type == CardType.EPIC) {
      return VORHABEN;
    }
    Matcher m = PRAEFIX.matcher(titel);
    return m.find() ? valueOf(m.group(1).toUpperCase(Locale.ROOT)) : ARBEITSPAKET;
  }
}
