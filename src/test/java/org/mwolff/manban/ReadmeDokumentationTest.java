package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hält das README an die Zusagen, die ein Fremder dort lesen soll, bevor er sie stillschweigend
 * anders annimmt (Issue #1285, Plan #1259).
 *
 * <p>Die drei Verneinungen des Erwartungsabsatzes sind wörtlich geprüft: Wer sie weichspült, sagt
 * wieder nichts darüber, was das Projekt nicht zusagt. Die verwiesenen Dateien müssen auf der
 * Platte liegen — ein Meldeweg, der ins Leere zeigt, ist schlechter als keiner.
 */
class ReadmeDokumentationTest {

  private static final Path README = Path.of("README.md");

  private static final String SCHNELLSTART = "Schnellstart";
  private static final String WARUM = "Warum kanban-kit";
  private static final String ERWARTUNG = "Was du erwarten darfst";
  private static final String SCHLUSS = "Mitwirken, Kontakt und Lizenz";

  private static final String WHITEPAPER =
      "https://mwolff.org/whitepapers/whitepaper-ki-entwicklungsprozess-v1.2.pdf";
  private static final String DOKU_SITE = "https://docs.mwolff.org";

  private static final List<String> VERWIESENE_DATEIEN =
      List.of("CONTRIBUTING.md", "CODE_OF_CONDUCT.md", "SECURITY.md", "LICENSE");

  @Test
  void derSchnellstartStehtVorWarumUndErwartung() throws IOException {
    List<String> ueberschriften = ueberschriften();

    assertThat(ueberschriften)
        .as("Abschnittsfolge in %s", README)
        .containsSubsequence(SCHNELLSTART, WARUM, ERWARTUNG);
    assertThat(ueberschriften.indexOf(WARUM))
        .as("%s steht direkt unter %s", WARUM, SCHNELLSTART)
        .isEqualTo(ueberschriften.indexOf(SCHNELLSTART) + 1);
  }

  @Test
  void derWarumAbsatzVerweistAufWhitepaperUndDokuSite() throws IOException {
    assertThat(abschnitt(WARUM))
        .as("Verweise im Abschnitt %s", WARUM)
        .contains(WHITEPAPER)
        .contains(DOKU_SITE);
  }

  @Test
  void derErwartungsabsatzSagtWasNichtZugesagtIst() throws IOException {
    assertThat(abschnitt(ERWARTUNG))
        .as("Zusagen im Abschnitt %s", ERWARTUNG)
        .contains("keine allgemeine Support-Zusage")
        .contains("keine Roadmap")
        .contains("keine allgemeine Reaktionszeit")
        .contains("SECURITY.md");
  }

  @Test
  void dieVerwiesenenDateienSindVerlinktUndVorhanden() throws IOException {
    String readme = Files.readString(README, StandardCharsets.UTF_8);

    for (String datei : VERWIESENE_DATEIEN) {
      assertThat(readme).as("Verweis auf %s in %s", datei, README).contains("(" + datei + ")");
      assertThat(Path.of(datei)).as("Verwiesene Datei %s", datei).exists();
    }
  }

  @Test
  void dieDreiMeldewegeStehenZusammenImSchlussabschnitt() throws IOException {
    assertThat(abschnitt(SCHLUSS))
        .as("Meldewege im Abschnitt %s", SCHLUSS)
        .contains(".github/ISSUE_TEMPLATE")
        .contains("SECURITY.md")
        .contains("info@mwolff.org");
  }

  @Test
  void dieLizenzStehtNurImSchlussabschnitt() throws IOException {
    List<String> ueberschriften = ueberschriften();

    assertThat(ueberschriften).as("Abschnitte in %s", README).doesNotContain("Lizenz");
    assertThat(ueberschriften.getLast()).as("Letzter Abschnitt in %s", README).isEqualTo(SCHLUSS);
    assertThat(abschnitt(SCHLUSS))
        .as("Lizenzangabe im Abschnitt %s", SCHLUSS)
        .contains("MIT-Lizenz")
        .contains("Copyright (c) 2026 Manfred Wolff");
  }

  /** Die Titel der {@code ##}-Überschriften, in der Reihenfolge des README. */
  private static List<String> ueberschriften() throws IOException {
    return Files.readAllLines(README, StandardCharsets.UTF_8).stream()
        .filter(zeile -> zeile.startsWith("## "))
        .map(zeile -> zeile.substring(3).strip())
        .toList();
  }

  /** Der Text zwischen {@code ## titel} und der nächsten {@code ##}-Überschrift. */
  private static String abschnitt(String titel) throws IOException {
    StringBuilder text = new StringBuilder();
    boolean drin = false;
    for (String zeile : Files.readAllLines(README, StandardCharsets.UTF_8)) {
      if (zeile.startsWith("## ")) {
        drin = zeile.substring(3).strip().equals(titel);
        continue;
      }
      if (drin) {
        text.append(zeile).append('\n');
      }
    }
    assertThat(text).as("Abschnitt ## %s in %s", titel, README).isNotEmpty();
    return text.toString();
  }
}
