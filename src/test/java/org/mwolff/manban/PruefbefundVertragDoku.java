package org.mwolff.manban;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest die normative Vertragsdoku {@code docs/pruefbefund-vertrag-v1.md} strukturiert aus (Issue
 * #585).
 *
 * <p>Gelesen wird über Überschriften, Tabellenzeilen und Listenpunkte statt über Suche im
 * Fließtext: Eine Umformulierung soll den Test nicht brechen, eine gestrichene Tabellenzeile schon.
 */
final class PruefbefundVertragDoku {

  static final Path PFAD = Path.of("docs", "pruefbefund-vertrag-v1.md");

  private static final Pattern UEBERSCHRIFT = Pattern.compile("^(#{1,6}) ");
  private static final Pattern FEHLERCODE_ZELLE = Pattern.compile("^\\|\\s*`([A-Z_]+)`");
  private static final Pattern LISTENEINTRAG = Pattern.compile("^- `([^`]+)`");

  private PruefbefundVertragDoku() {}

  static String text() {
    return PruefbefundVertragMaterial.lies(PFAD);
  }

  /** Die Fehlercodes der normativen Fehlertabelle, aufsteigend und ohne Wiederholung. */
  static List<String> fehlercodes() {
    List<String> codes = new ArrayList<>();
    for (String zeile : abschnitt("## Fehlertabelle")) {
      Matcher zelle = FEHLERCODE_ZELLE.matcher(zeile);
      if (zelle.find()) {
        codes.add(zelle.group(1));
      }
    }
    return List.copyOf(new TreeSet<>(codes));
  }

  /** Die abschließend als geschützt aufgezählten Kennzeichnungszeilen. */
  static List<String> geschuetzteMarker() {
    List<String> marker = new ArrayList<>();
    for (String zeile : abschnitt("### Normativ geschützt")) {
      Matcher eintrag = LISTENEINTRAG.matcher(zeile);
      if (eintrag.find()) {
        marker.add(eintrag.group(1));
      }
    }
    return marker;
  }

  static List<String> ueberschriften() {
    List<String> ueberschriften = new ArrayList<>();
    for (String zeile : text().split("\n", -1)) {
      if (UEBERSCHRIFT.matcher(zeile).find()) {
        ueberschriften.add(zeile);
      }
    }
    return ueberschriften;
  }

  /** Die Zeilen eines Abschnitts bis zur nächsten Überschrift derselben oder höherer Ebene. */
  private static List<String> abschnitt(String ueberschrift) {
    int ebene = ueberschrift.indexOf(' ');
    List<String> zeilen = new ArrayList<>();
    boolean gefunden = false;
    for (String zeile : text().split("\n", -1)) {
      if (gefunden) {
        Matcher naechste = UEBERSCHRIFT.matcher(zeile);
        if (naechste.find() && naechste.group(1).length() <= ebene) {
          break;
        }
        zeilen.add(zeile);
      } else if (ueberschrift.equals(zeile.trim())) {
        gefunden = true;
      }
    }
    if (!gefunden) {
      throw new AssertionError("Abschnitt fehlt in " + PFAD + ": " + ueberschrift);
    }
    return zeilen;
  }
}
