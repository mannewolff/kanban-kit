package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Hält den Befund {@code docs/befund-interaktive-sitzungen.md} in der Form, in der spätere Pakete
 * ihn zitieren können (Issue #1008).
 *
 * <p>Der Befund ist kein Fließtext, sondern eine Auskunft mit fester Form: drei Abschnitte in
 * fester Reihenfolge, und in zwei von ihnen eine Ergebniszeile, die eine von genau zwei möglichen
 * Antworten gibt. Genau daran hängen die Folgepakete — ob eine Preistabelle je Modell nötig wird
 * und ob Worktree-Sitzungen überhaupt melden können. Ein Befund, der die Zeile umformuliert oder
 * beide Varianten stehen lässt, ist maschinell nicht mehr auswertbar und lässt die Folgepakete
 * wieder raten.
 *
 * <p>Geprüft wird die <b>Form</b>, nicht der Inhalt: Dass jeder Abschnitt einen Pfad und einen
 * Feldnamen nennt, unterscheidet einen Befund von einer Behauptung. Ob das im ersten Abschnitt
 * abgedruckte Kommando dieselben Feldnamen liefert, ist kein Test — es liest ein Sitzungsprotokoll
 * im Home-Verzeichnis, das auf keiner anderen Maschine existiert.
 */
class BefundInteraktiveSitzungenTest {

  private static final Path BEFUND = Path.of("docs", "befund-interaktive-sitzungen.md");
  private static final Path INDEX = Path.of("docs", "index.md");

  private static final String ABSCHNITT_VERBRAUCH = "## Verbrauchsangaben im Sitzungsprotokoll";
  private static final String ABSCHNITT_HOOKS = "## Hook-Ereignisse SessionEnd und Stop";
  private static final String ABSCHNITT_WORKTREES = "## Worktrees";

  private static final String VERBRAUCH_MIT_BETRAG =
      "Ergebnis: Dollarbetrag im Protokoll vorhanden — Feld ";
  private static final String VERBRAUCH_OHNE_BETRAG =
      "Ergebnis: Kein Dollarbetrag im Protokoll — Preistabelle noetig.";
  private static final String WORKTREE_MIT_KIT =
      "Ergebnis: Worktree traegt Kit und settings.json — Sitzungen melden.";
  private static final String WORKTREE_OHNE_KIT =
      "Ergebnis: Worktree traegt kein Kit — Worktree-Sitzungen bleiben unerfasst.";

  /** Ein Pfad ist eine in Backticks gesetzte Zeichenfolge mit mindestens einem Schrägstrich. */
  private static final Pattern PFAD_IN_BACKTICKS = Pattern.compile("`[^`\\s]*/[^`\\s]*`");

  /** Ein Feldname ist ein in Backticks gesetzter Bezeichner in Schlangenschrift. */
  private static final Pattern FELDNAME_IN_BACKTICKS =
      Pattern.compile("`[a-z][a-z0-9]*(?:_[a-z0-9]+)+`");

  private static final List<String> VERBOTEN = List.of("TODO", "tbd", "<feldname>");

  @Test
  void derBefundTraegtDieDreiUeberschriftenInDerVorgegebenenReihenfolge() throws IOException {
    assertThat(BEFUND).as("Befunddatei").exists();

    assertThat(ueberschriften())
        .as("Abschnitte zweiter Ebene in %s", BEFUND)
        .containsExactly(ABSCHNITT_VERBRAUCH, ABSCHNITT_HOOKS, ABSCHNITT_WORKTREES);
  }

  @Test
  void derVerbrauchsabschnittEndetMitGenauEinerDerBeidenErgebniszeilen() throws IOException {
    String letzte = letzteZeile(ABSCHNITT_VERBRAUCH);

    assertThat(letzte)
        .as("Schlusszeile von %s", ABSCHNITT_VERBRAUCH)
        .satisfiesAnyOf(
            zeile -> assertThat(zeile).startsWith(VERBRAUCH_MIT_BETRAG).endsWith("."),
            zeile -> assertThat(zeile).isEqualTo(VERBRAUCH_OHNE_BETRAG));
  }

  @Test
  void derWorktreeAbschnittEndetMitGenauEinerDerBeidenErgebniszeilen() throws IOException {
    assertThat(letzteZeile(ABSCHNITT_WORKTREES))
        .as("Schlusszeile von %s", ABSCHNITT_WORKTREES)
        .isIn(WORKTREE_MIT_KIT, WORKTREE_OHNE_KIT);
  }

  @Test
  void keineErgebniszeileStehtDoppelt() throws IOException {
    List<String> text = zeilen();

    for (String ergebnis : List.of(VERBRAUCH_OHNE_BETRAG, WORKTREE_MIT_KIT, WORKTREE_OHNE_KIT)) {
      assertThat(text.stream().filter(zeile -> zeile.strip().equals(ergebnis)).count())
          .as("Vorkommen von \"%s\" in %s", ergebnis, BEFUND)
          .isLessThanOrEqualTo(1);
    }
    assertThat(
            text.stream().filter(zeile -> zeile.strip().startsWith(VERBRAUCH_MIT_BETRAG)).count())
        .as("Vorkommen der Ergebniszeile mit Dollarbetrag in %s", BEFUND)
        .isLessThanOrEqualTo(1);
  }

  @Test
  void jederAbschnittNenntMindestensEinenPfadUndMindestensEinenFeldnamen() throws IOException {
    Map<String, List<String>> abschnitte = abschnitte();

    List<String> ohnePfad = new ArrayList<>();
    List<String> ohneFeldname = new ArrayList<>();
    abschnitte.forEach(
        (ueberschrift, rumpf) -> {
          String text = String.join("\n", rumpf);
          if (!PFAD_IN_BACKTICKS.matcher(text).find()) {
            ohnePfad.add(ueberschrift);
          }
          if (!FELDNAME_IN_BACKTICKS.matcher(text).find()) {
            ohneFeldname.add(ueberschrift);
          }
        });

    assertThat(ohnePfad).as("Abschnitte ohne konkreten Dateipfad").isEmpty();
    assertThat(ohneFeldname).as("Abschnitte ohne konkreten Feldnamen").isEmpty();
  }

  @Test
  void derBefundEnthaeltKeinePlatzhalter() throws IOException {
    String text = Files.readString(BEFUND, StandardCharsets.UTF_8);

    for (String verboten : VERBOTEN) {
      assertThat(text.toLowerCase(Locale.ROOT))
          .as("Platzhalter \"%s\" in %s", verboten, BEFUND)
          .doesNotContain(verboten.toLowerCase(Locale.ROOT));
    }
  }

  @Test
  void dasInhaltsverzeichnisVerweistAufDenBefund() throws IOException {
    assertThat(Files.readString(INDEX, StandardCharsets.UTF_8))
        .as("Verweis auf den Befund in %s", INDEX)
        .contains(BEFUND.getFileName().toString());
  }

  private static List<String> zeilen() throws IOException {
    return Files.readAllLines(BEFUND, StandardCharsets.UTF_8);
  }

  private static List<String> ueberschriften() throws IOException {
    return zeilen().stream().filter(zeile -> zeile.startsWith("## ")).map(String::strip).toList();
  }

  /** Zerlegt den Befund in seine Abschnitte zweiter Ebene: Überschrift → Zeilen des Rumpfs. */
  private static Map<String, List<String>> abschnitte() throws IOException {
    Map<String, List<String>> abschnitte = new LinkedHashMap<>();
    List<String> aktuell = null;
    for (String zeile : zeilen()) {
      if (zeile.startsWith("## ")) {
        aktuell = new ArrayList<>();
        abschnitte.put(zeile.strip(), aktuell);
      } else if (aktuell != null) {
        aktuell.add(zeile);
      }
    }
    return abschnitte;
  }

  private static String letzteZeile(String ueberschrift) throws IOException {
    List<String> rumpf = abschnitte().get(ueberschrift);
    assertThat(rumpf).as("Abschnitt %s in %s", ueberschrift, BEFUND).isNotNull();

    List<String> gefuellt = rumpf.stream().map(String::strip).filter(z -> !z.isEmpty()).toList();
    assertThat(gefuellt).as("Inhalt von %s", ueberschrift).isNotEmpty();
    return gefuellt.getLast();
  }
}
