package org.mwolff.manban.entscheidungskette.domain;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Die Zahl der Funde je Schwere nach der Grammatik des Fundblocks aus {@code kit/findings.mjs}
 * (Plan #2108 E9).
 *
 * <p>Ein Abschnitt beginnt bei einer Marke am Zeilenanfang und endet vor der nächsten Überschrift
 * ohne Marke. Darin ist jede ganz fett gesetzte Zeile ein eigener Fund. Eine Zeile, die außer der
 * Marke nichts trägt, ist eine Gruppenüberschrift: Folgt ihr vor jedem Inhalt eine fette Kopfzeile,
 * zählt sie nicht mit.
 *
 * @param kritisch Funde der Marke {@code KRITISCH}
 * @param blocker Funde der Marke {@code BLOCKER}
 * @param wichtig Funde der Marke {@code WICHTIG}
 * @param hinweis Funde der Marke {@code HINWEIS}
 */
public record Fundzahlen(int kritisch, int blocker, int wichtig, int hinweis) {

  private static final Pattern MARKE =
      Pattern.compile("(KRITISCH|BLOCKER|WICHTIG|HINWEIS)(?![A-Za-zÄÖÜäöü])");
  private static final Pattern LISTE_VORN = Pattern.compile("^\\s*(?:[-+*]|\\d+[.)])\\s+");
  private static final Pattern DEKO_VORN = Pattern.compile("^[\\s>#*_]+");
  private static final Pattern KOPFZEILE = Pattern.compile("\\*\\*.+\\*\\*");
  private static final String DEKO_HINTEN = "*_#";

  /** Zählt die Funde in den Zeilen eines Texts. */
  public static Fundzahlen aus(List<String> zeilen) {
    Zaehlung zaehlung = new Zaehlung();
    zeilen.forEach(zaehlung::lies);
    return zaehlung.ergebnis();
  }

  /** Alle Funde zusammen. */
  public int gesamt() {
    return kritisch + blocker + wichtig + hinweis;
  }

  /** Ob es keinen Fund gibt. */
  public boolean keine() {
    return gesamt() == 0;
  }

  private static String ohneListenmarke(String zeile) {
    return LISTE_VORN.matcher(zeile).replaceFirst("");
  }

  /** Die Zeile ohne Markdown an beiden Enden; Listenmarke und Dekoration umschließen einander. */
  private static String kern(String zeile) {
    String vorn = zeile;
    String kuerzer = kuerzen(vorn);
    while (!kuerzer.equals(vorn)) {
      vorn = kuerzer;
      kuerzer = kuerzen(vorn);
    }
    int ende = vorn.length();
    while (ende > 0 && istDekoHinten(vorn.charAt(ende - 1))) {
      ende--;
    }
    return vorn.substring(0, ende);
  }

  private static String kuerzen(String zeile) {
    return DEKO_VORN.matcher(ohneListenmarke(zeile)).replaceFirst("");
  }

  private static boolean istDekoHinten(char zeichen) {
    return Character.isWhitespace(zeichen) || DEKO_HINTEN.indexOf(zeichen) >= 0;
  }

  /** Der Zustand beim Durchgehen der Zeilen. */
  private static final class Zaehlung {
    private int anzahlKritisch;
    private int anzahlBlocker;
    private int anzahlWichtig;
    private int anzahlHinweis;
    private @Nullable String abschnitt;
    private boolean nurUeberschrift;

    void lies(String zeile) {
      String roh = zeile.strip();
      String kern = kern(zeile);
      Matcher marke = MARKE.matcher(kern);
      if (marke.lookingAt()) {
        abschnitt = marke.group(1);
        zaehle(abschnitt, 1);
        nurUeberschrift = kern.equals(abschnitt);
      } else if (roh.startsWith("#")) {
        abschnitt = null;
        nurUeberschrift = false;
      } else if (abschnitt != null && KOPFZEILE.matcher(ohneListenmarke(roh)).matches()) {
        zaehle(abschnitt, nurUeberschrift ? 0 : 1);
        nurUeberschrift = false;
      } else if (!roh.isEmpty()) {
        nurUeberschrift = false;
      }
    }

    private void zaehle(String schwere, int um) {
      switch (schwere) {
        case "KRITISCH" -> anzahlKritisch += um;
        case "BLOCKER" -> anzahlBlocker += um;
        case "WICHTIG" -> anzahlWichtig += um;
        default -> anzahlHinweis += um;
      }
    }

    Fundzahlen ergebnis() {
      return new Fundzahlen(anzahlKritisch, anzahlBlocker, anzahlWichtig, anzahlHinweis);
    }
  }
}
