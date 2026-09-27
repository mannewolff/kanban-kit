package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.project.domain.Permission;

/**
 * Hält die ausgelieferte Rechte-Übersicht {@code docs/rollen-und-rechte.md} an {@link Permission}
 * gekoppelt: Wer ein Recht ergänzt, ohne die Matrix nachzuziehen, bekommt einen roten Build statt
 * einer stillen Drift zwischen Code und Dokumentation (Issue #437).
 *
 * <p>Die Matrix nennt jedes Recht mit seinem technischen Schlüssel, deshalb genügt die Suche nach
 * dem Enum-Namen. Bewusst keine handgepflegte Liste erwarteter Zeilen: geprüft wird gegen die
 * Enum-Werte selbst, damit die Leitplanke bei jeder Erweiterung automatisch mitwächst.
 *
 * <p>Die Gegenrichtung prüft {@link #keineMatrixZeileOhneSchluessel()} (Issue #1165): Eine Zeile
 * <em>ohne</em> Schlüssel beschreibt eine Sonderregel, die die {@code /roles}-Ansicht gar nicht
 * rendern kann — sie liest allein {@code GET /api/roles/matrix}, und dort steht nur, was einen
 * Schlüssel hat. Wer so eine Zeile in die Matrix schreibt, hält ein Recht für vergeben, das die App
 * nie zeigt.
 */
class RightsDocumentationTest {

  /**
   * Einzige Rechte-Übersicht im Repository (wird über VitePress unter {@code /docs/}
   * bereitgestellt).
   */
  private static final Path RECHTE_UEBERSICHT = Path.of("docs", "rollen-und-rechte.md");

  /** Die Wurzel-Datei war die zweite, driftende Kopie derselben Übersicht (Issue #437). */
  private static final Path ENTFERNTE_KOPIE = Path.of("rollen_rechte.md");

  /** Kopfzeile der Rechte-Matrix — Anker für das Einlesen ihrer Zeilen. */
  private static final String MATRIX_KOPF =
      "| Recht | Schlüssel | VIEWER | MEMBER | ADMIN | OWNER |";

  /**
   * Eine Matrix-Zeile mit Schlüssel: erste Spalte beliebig, zweite Spalte der Enum-Name in
   * Backticks, z. B. {@code | Karte verschieben | `CARD_MOVE` | … }.
   */
  private static final Pattern ZEILE_MIT_SCHLUESSEL =
      Pattern.compile("^\\|[^|]*\\|\\s*`[A-Z_]+`\\s*\\|");

  @Test
  void jedesRechtStehtInDerRechteUebersicht() throws IOException {
    String doku = Files.readString(RECHTE_UEBERSICHT, StandardCharsets.UTF_8);

    List<String> fehlend =
        Arrays.stream(Permission.values())
            .map(Enum::name)
            .filter(key -> !doku.contains(key))
            .toList();

    assertThat(fehlend).as("Rechte ohne Zeile in %s", RECHTE_UEBERSICHT).isEmpty();
  }

  @Test
  void keineMatrixZeileOhneSchluessel() throws IOException {
    List<String> zeilen = Files.readAllLines(RECHTE_UEBERSICHT, StandardCharsets.UTF_8);
    int kopf = zeilen.indexOf(MATRIX_KOPF);
    assertThat(kopf).as("Kopfzeile der Rechte-Matrix in %s", RECHTE_UEBERSICHT).isNotNegative();

    // Ab der Zeile nach Kopf und Trennzeile bis zur ersten Zeile, die keine Tabellenzeile ist.
    List<String> ohneSchluessel =
        zeilen.stream()
            .skip(kopf + 2L)
            .takeWhile(zeile -> zeile.startsWith("|"))
            .filter(zeile -> !ZEILE_MIT_SCHLUESSEL.matcher(zeile).find())
            .toList();

    assertThat(ohneSchluessel)
        .as("Matrix-Zeilen ohne technischen Schlüssel in %s", RECHTE_UEBERSICHT)
        .isEmpty();
  }

  @Test
  void esGibtNurEineRechteUebersicht() {
    assertThat(ENTFERNTE_KOPIE)
        .as("Zweite Rechte-Übersicht — Quelle ist %s", RECHTE_UEBERSICHT)
        .doesNotExist();
  }
}
