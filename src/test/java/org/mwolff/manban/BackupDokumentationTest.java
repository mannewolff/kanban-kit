package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Hält die Betriebsdokumentation der Sicherung an das, was der Betrieb tatsächlich verlangt (Issue
 * #831, Plan #825).
 *
 * <p>Der Anlass ist der gefährlichste Satz des ganzen Vorhabens: Nach einer Sicherung hält der
 * Server nur den öffentlichen Schlüssel. Wer den privaten verliert, hat Sicherungen, die niemand
 * mehr öffnen kann. Eine Anleitung, die diesen Satz nicht führt, ist kein Schönheitsfehler — sie
 * ist der Unterschied zwischen einer Rückholung und einem Totalverlust.
 *
 * <p><b>Maßstab der Variablen ist {@code .env.example}</b>, keine im Test wiederholte Aufzählung:
 * Kommt eine sechste {@code MANBAN_BACKUP_*}-Variable hinzu, wächst die Leitplanke mit, statt eine
 * zweite Liste zu führen, die driftet. Gezählt werden nur Zuweisungszeilen — die Kommentare der
 * Vorlage nennen auch {@code MANBAN_BACKUP_ENABLED}, das bewusst nirgends von Hand gesetzt wird.
 *
 * <p>Dass der Wiederherstellungsnachweis <b>kein eigenes Dokument</b> wird, ist ebenfalls geprüft
 * (Plan #825 E15): {@code docs-site/copy-docs.mjs} kopiert alles aus {@code docs/} auf die
 * öffentliche Doku-Site, und ein Protokoll des Rückholvorgangs nennt Infrastruktur-Details der
 * eigenen Instanz.
 */
class BackupDokumentationTest {

  private static final Path VORLAGE = Path.of(".env.example");
  private static final Path BETRIEB = Path.of("docs", "betrieb.md");
  private static final Path ANLEITUNG = Path.of("docs", "backup.md");
  private static final Path UEBERSICHT = Path.of("docs", "index.md");
  private static final Path README = Path.of("README.md");
  private static final Path DOKU_SITE = Path.of("docs-site", ".vitepress", "config.ts");

  /** Der Nachweis gehört in die Betriebsseite, nicht in eine eigene Datei (E15). */
  private static final Path VERBOTENER_NACHWEIS = Path.of("docs", "backup-restore-nachweis.md");

  private static final Pattern ZUWEISUNG = Pattern.compile("^(MANBAN_BACKUP_[A-Z0-9_]+)=");
  private static final Pattern VARIABLE = Pattern.compile("MANBAN_[A-Z0-9_]+");

  @Test
  void jedeSicherungsvariableStehtInDerTabelleDerBetriebsseite() throws IOException {
    Set<String> ausVorlage = sicherungsvariablen();
    Set<String> dokumentiert =
        ausTabellenzeilen(Files.readAllLines(BETRIEB, StandardCharsets.UTF_8));

    assertThat(ausVorlage).as("Sicherungsvariablen in %s", VORLAGE).hasSizeGreaterThanOrEqualTo(5);
    assertThat(dokumentiert)
        .as("In %s gesetzt, aber nicht in den Tabellen von %s", VORLAGE, BETRIEB)
        .containsAll(ausVorlage);
  }

  @Test
  void dieBetriebsseiteFuehrtDenLetztenWiederherstellungsnachweis() throws IOException {
    String betrieb = Files.readString(BETRIEB, StandardCharsets.UTF_8);

    assertThat(betrieb)
        .as("Überschrift des Nachweises in %s", BETRIEB)
        .contains("## Letzter Wiederherstellungsnachweis");
  }

  @Test
  void derNachweisBekommtKeineEigeneSeite() {
    assertThat(VERBOTENER_NACHWEIS)
        .as("Der Nachweis gehört in %s, nicht auf die öffentliche Doku-Site (E15)", BETRIEB)
        .doesNotExist();
  }

  @Test
  void dieAnleitungTraegtAlleVierAbschnitte() throws IOException {
    List<String> ueberschriften =
        Files.readAllLines(ANLEITUNG, StandardCharsets.UTF_8).stream()
            .filter(zeile -> zeile.startsWith("#"))
            .toList();

    assertThat(ueberschriften)
        .as("Abschnitte in %s", ANLEITUNG)
        .anyMatch(zeile -> zeile.contains("Einrichtung"))
        .anyMatch(zeile -> zeile.contains("Schlüssel"))
        .anyMatch(zeile -> zeile.contains("Rückholung"))
        .anyMatch(zeile -> zeile.contains("Verfallen"));
  }

  @Test
  void dieAnleitungIstVonUeberallVerlinkt() throws IOException {
    assertThat(Files.readString(UEBERSICHT, StandardCharsets.UTF_8))
        .as("Verweis auf die Anleitung in %s", UEBERSICHT)
        .contains("(backup.md)");
    assertThat(Files.readString(README, StandardCharsets.UTF_8))
        .as("Verweis auf die Anleitung in %s", README)
        .contains("(docs/backup.md)");

    long stellen =
        Files.readAllLines(DOKU_SITE, StandardCharsets.UTF_8).stream()
            .filter(zeile -> zeile.contains("\"/backup\""))
            .count();

    assertThat(stellen)
        .as(
            "Die Seite muss in %s sowohl in der Navigation als auch in der Seitenleiste stehen",
            DOKU_SITE)
        .isGreaterThanOrEqualTo(2);
  }

  /** Die gesetzten {@code MANBAN_BACKUP_*}-Variablen der Vorlage — ohne die aus den Kommentaren. */
  private static Set<String> sicherungsvariablen() throws IOException {
    Set<String> namen = new LinkedHashSet<>();
    for (String zeile : Files.readAllLines(VORLAGE, StandardCharsets.UTF_8)) {
      Matcher treffer = ZUWEISUNG.matcher(zeile.strip());
      if (treffer.find()) {
        namen.add(treffer.group(1));
      }
    }
    return namen;
  }

  /**
   * Wie in {@link BetriebsvariablenDocumentationTest}: nur Tabellenzeilen zählen als Zusicherung.
   */
  private static Set<String> ausTabellenzeilen(List<String> zeilen) {
    Set<String> namen = new LinkedHashSet<>();
    for (String zeile : zeilen) {
      if (!zeile.strip().startsWith("|")) {
        continue;
      }
      Matcher treffer = VARIABLE.matcher(zeile);
      while (treffer.find()) {
        namen.add(treffer.group());
      }
    }
    return namen;
  }
}
