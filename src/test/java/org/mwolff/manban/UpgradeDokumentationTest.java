package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hält das Upgrade-Wissen an genau einem Ort fest (Issue #1268, Plan #1262, fachlich #675).
 *
 * <p>Der Anlass: Upgrade-Hinweise standen in {@code CHANGELOG.md} <em>und</em> in {@code
 * docs/betrieb.md}. Zwei Orte laufen auseinander, und ein Selbsthoster weiß vor einem
 * Versionssprung nicht, welchen er lesen muss. Seit diesem Paket ist {@code UPGRADING.md} der
 * einzige Ort; die beiden Altstellen sind Verweise.
 *
 * <p>Die schärfste Prüfung ist die <b>Reihenfolge</b> im Abschnitt zum Datenbankkennwort: {@code
 * POSTGRES_PASSWORD} liest das Postgres-Abbild nur beim Anlegen eines leeren Datenverzeichnisses.
 * Eine Anleitung, die zuerst die {@code .env} ändern lässt und erst danach die Rolle, hinterlässt
 * eine Anwendung, die mit dem neuen Kennwort gegen die alte Rolle scheitert. Deshalb muss {@code
 * ALTER ROLE} im Dokument vor der ersten Nennung von {@code POSTGRES_PASSWORD} stehen.
 */
class UpgradeDokumentationTest {

  private static final Path UPGRADING = Path.of("UPGRADING.md");
  private static final Path CHANGELOG = Path.of("CHANGELOG.md");
  private static final Path BETRIEB = Path.of("docs", "betrieb.md");
  private static final Path UEBERSICHT = Path.of("docs", "index.md");
  private static final Path README = Path.of("README.md");
  private static final Path KOPIERER = Path.of("docs-site", "copy-docs.mjs");
  private static final Path DOKU_SITE = Path.of("docs-site", ".vitepress", "config.ts");

  private static final String REGEL =
      "Eine Version ohne eigenen Abschnitt verlangt keine Handarbeit.";

  @Test
  void upgradingFuehrtDieRegelFuerVersionenOhneEigenenAbschnitt() throws IOException {
    assertThat(lies(UPGRADING)).as("Regel in %s", UPGRADING).contains(REGEL);
  }

  @Test
  void dasDatenbankkennwortWirdInDerRolleVorDerEnvGeaendert() throws IOException {
    List<String> zeilen = Files.readAllLines(UPGRADING, StandardCharsets.UTF_8);

    int alterRole = erstesVorkommen(zeilen, "ALTER ROLE");
    int envWert = erstesVorkommen(zeilen, "POSTGRES_PASSWORD");

    assertThat(alterRole).as("ALTER ROLE in %s", UPGRADING).isGreaterThanOrEqualTo(0);
    assertThat(envWert).as("POSTGRES_PASSWORD in %s", UPGRADING).isGreaterThanOrEqualTo(0);
    assertThat(alterRole)
        .as("ALTER ROLE muss in %s vor der ersten Nennung von POSTGRES_PASSWORD stehen", UPGRADING)
        .isLessThan(envWert);
    assertThat(lies(UPGRADING))
        .as("Der Weg in die laufende Datenbank steht in %s", UPGRADING)
        .contains("docker compose exec postgres");
  }

  @Test
  void upgradingNenntDieBeidenAltfaelleMitIhrerVersion() throws IOException {
    String text = lies(UPGRADING);

    assertThat(text).as("Version der SeaweedFS-Umstellung").contains("2.12.0");
    assertThat(text).as("Version der Sitzungsschlüssel-Startbedingung").contains("1.44.0");
    assertThat(text).as("Startbedingung Sitzungsschlüssel").contains("MANBAN_SESSION_SECRET");
  }

  @Test
  void derChangelogVerweistNurNochAufUpgrading() throws IOException {
    List<String> abschnitt = abschnitt(CHANGELOG, "## Hinweise zum Upgrade");

    assertThat(abschnitt).as("Abschnitt in %s", CHANGELOG).isNotEmpty();
    assertThat(String.join("\n", abschnitt))
        .as("Verweis statt Inhalt in %s", CHANGELOG)
        .contains("UPGRADING.md")
        .doesNotContain("Wartungsfenster")
        .doesNotContain("MANBAN_STORAGE_");
  }

  @Test
  void dieBetriebsseiteVerweistNurNochAufUpgrading() throws IOException {
    List<String> abschnitt = abschnitt(BETRIEB, "## Upgrade-Hinweise");

    assertThat(abschnitt).as("Abschnitt in %s", BETRIEB).isNotEmpty();
    assertThat(String.join("\n", abschnitt))
        .as("Verweis statt Inhalt in %s", BETRIEB)
        .contains("UPGRADING.md")
        .doesNotContain("MANBAN_SESSION_SECRET");
  }

  @Test
  void dieGerenderteDokuFuehrtDieSeiteUndBekommtIhreQuelle() throws IOException {
    assertThat(lies(KOPIERER))
        .as("%s kopiert die Wurzeldatei mit", KOPIERER)
        .contains("../UPGRADING.md")
        .contains("upgrading.md");
    assertThat(lies(DOKU_SITE))
        .as("Nav und Sidebar in %s führen /upgrading", DOKU_SITE)
        .contains("/upgrading");
    assertThat(lies(UEBERSICHT)).as("Verweis in %s", UEBERSICHT).contains("upgrading.md");
    assertThat(lies(README)).as("Verweis in %s", README).contains("UPGRADING.md");
  }

  private static String lies(Path pfad) throws IOException {
    return Files.readString(pfad, StandardCharsets.UTF_8);
  }

  private static int erstesVorkommen(List<String> zeilen, String muster) {
    for (int i = 0; i < zeilen.size(); i++) {
      if (zeilen.get(i).contains(muster)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Die Zeilen zwischen einer Überschrift und der nächsten Überschrift derselben oder höherer
   * Ebene.
   */
  private static List<String> abschnitt(Path pfad, String ueberschrift) throws IOException {
    List<String> zeilen = Files.readAllLines(pfad, StandardCharsets.UTF_8);
    int start = erstesVorkommen(zeilen, ueberschrift);
    if (start < 0) {
      return List.of();
    }
    int ende = start + 1;
    while (ende < zeilen.size() && !zeilen.get(ende).startsWith("## ")) {
      ende++;
    }
    return zeilen.subList(start + 1, ende);
  }
}
