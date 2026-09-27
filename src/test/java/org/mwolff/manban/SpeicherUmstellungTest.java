package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Hält den einmaligen Umzug der Anhänge in den neuen Objektspeicher an das, was er dem Betreiber
 * verspricht (Issue #1230, Plan #1222, E13/E14/E18).
 *
 * <p>Der Anlass: Nach dem Update sollen es dieselben Anhänge mit derselben Vorschau sein.
 * Dazwischen liegt genau ein Umzug, und er läuft auf einem Server, dessen Deploy bei jedem Push auf
 * {@code production} selbsttätig {@code git reset --hard} und {@code up -d --build} fährt (E18).
 * Der Umstieg <b>ist</b> damit der Merge — nicht ein Handgriff im Wartungsfenster. Eine Anleitung,
 * die das nicht so beschreibt, beschreibt einen Umzug, den es auf diesem Server nicht gibt.
 *
 * <p>Geprüft wird der Dateitext, nicht die zusammengeführte Compose-Konfiguration: Der Test greift
 * ohne laufenden Docker-Daemon. Ablage im Wurzelpaket neben {@link BetriebsdateienTest} aus
 * demselben Grund wie dort — geprüft werden ausgelieferte Repository-Dateien, keine Fachlogik.
 *
 * <p>Diese Klasse entfällt zusammen mit {@code docker-compose.umzug.yml} und {@code
 * docker-compose.altspeicher.yml}, also mit Version 2.13.0: Sie prüft einen einmaligen Vorgang.
 */
class SpeicherUmstellungTest {

  /** Einmal-Dienst, der die Anhänge aus dem alten in den neuen Speicher kopiert. */
  private static final Path UMZUG_OVERLAY = Path.of("docker-compose.umzug.yml");

  /** Rückweg-Overlay mit dem alten Speicherdienst — der Umzug liest daraus. */
  private static final Path ALTSPEICHER_OVERLAY = Path.of("docker-compose.altspeicher.yml");

  private static final Path BETRIEB = Path.of("docs", "betrieb.md");

  /** Überschrift des Abschnitts, der den Umzug beschreibt. */
  private static final String ABSCHNITT = "## Umstellung des Objektspeichers";

  /** Name des Einmal-Dienstes. */
  private static final String UMZUGSDIENST = "manban-umzug";

  /**
   * Dienstname des alten Speichers im Netz des Stacks — die Adresse, die Anwendung und Spiegel
   * während der Vorkopie weiter lesen. Er steht in {@code docker-compose.altspeicher.yml}; dass
   * dieser Test denselben Namen meint, hält {@link #dasRueckwegOverlayFuehrtDenAltenDienst}
   * zusammen.
   */
  private static final String ALTER_DIENSTNAME = "minio";

  /**
   * Die Zuordnung alt → neu (E6). Die Liste steht hier ausgeschrieben, obwohl der Test sonst gegen
   * den Bestand messen würde: Die <b>alten</b> Namen gibt es im Bestand nicht mehr — genau das ist
   * ihr Zweck. Sechs feste, historische Namen, die sich nie wieder ändern; eine abgeleitete Quelle
   * gibt es dafür nicht.
   */
  private static final Map<String, String> ZUORDNUNG =
      Map.of(
          "MANBAN_MINIO_ENDPOINT", "MANBAN_STORAGE_ENDPOINT",
          "MANBAN_MINIO_ACCESS_KEY", "MANBAN_STORAGE_ACCESS_KEY",
          "MANBAN_MINIO_SECRET_KEY", "MANBAN_STORAGE_SECRET_KEY",
          "MANBAN_MINIO_BUCKET", "MANBAN_STORAGE_BUCKET",
          "MINIO_ROOT_USER", "OBJEKTSPEICHER_ROOT_USER",
          "MINIO_ROOT_PASSWORD", "OBJEKTSPEICHER_ROOT_PASSWORD");

  /** Standardwerte aus dem Repository — keiner davon darf im Umzugs-Overlay stehen. */
  private static final List<String> OEFFENTLICH_BEKANNTE_WERTE =
      List.of("manban-minio", "manban-objektspeicher", "change-me");

  @Test
  void dasUmzugsOverlayFaehrtEinenEinmalDienst() throws IOException {
    List<String> zeilen = wirksameZeilen(UMZUG_OVERLAY);

    assertThat(zeilen)
        .as("Einmal-Dienst %s in %s", UMZUGSDIENST, UMZUG_OVERLAY)
        .anySatisfy(zeile -> assertThat(zeile).isEqualTo(UMZUGSDIENST + ":"));
    assertThat(zeilen)
        .as("%s läuft einmal und kommt nicht von selbst wieder hoch", UMZUGSDIENST)
        .anySatisfy(zeile -> assertThat(zeile).isEqualTo("restart: \"no\""));
  }

  /**
   * Der Einmal-Dienst baut aus {@code backup/} statt ein Abbild zu ziehen (Issue #1230): Das
   * Sicherungs-Abbild bringt sein {@code rclone} schon mit. Ein {@code image:} wäre eine neue
   * fremde Bezugsquelle für einen einmaligen Vorgang — und müsste in {@code scripts/bausteine.json}
   * geführt und laufend auf Beziehbarkeit geprüft werden.
   */
  @Test
  void derUmzugBringtKeineNeueBezugsquelleMit() throws IOException {
    assertThat(wirksameZeilenMit(UMZUG_OVERLAY, "image:"))
        .as("%s zieht kein eigenes Abbild, sondern baut aus backup/", UMZUG_OVERLAY)
        .isEmpty();
    assertThat(wirksameZeilenMit(UMZUG_OVERLAY, "context:"))
        .as("Baukontext des Einmal-Dienstes in %s", UMZUG_OVERLAY)
        .anySatisfy(zeile -> assertThat(zeile).contains("./backup"));
  }

  /** Beide Seiten als rclone-S3-Remotes {@code alt} und {@code neu}. */
  @Test
  void derUmzugsDienstKenntBeideRemotes() throws IOException {
    String overlay = Files.readString(UMZUG_OVERLAY, StandardCharsets.UTF_8);

    for (String remote : List.of("ALT", "NEU")) {
      for (String feld :
          List.of("TYPE", "ENDPOINT", "ACCESS_KEY_ID", "SECRET_ACCESS_KEY", "PROVIDER")) {
        assertThat(overlay)
            .as("Remote %s braucht %s — sonst spricht rclone die Seite nicht an", remote, feld)
            .contains("RCLONE_CONFIG_" + remote + "_" + feld + ":");
      }
    }
  }

  /**
   * Die beiden Flags der Prüfung sind der Nachweis selbst (Issue #1230): {@code --one-way} prüft,
   * dass alles aus der Quelle im Ziel liegt, und stört sich nicht an Zusätzlichem dort — ein
   * Anhang, der zwischen Vorkopie und Umstieg gelöscht wurde, liegt im neuen Speicher noch als
   * verwaistes Objekt. Ohne das Flag bräche gerade der gelungene Umzug ab. {@code --download}
   * vergleicht byteweise: Die beiden Dienste bilden ETags nicht gleich, und ein {@code check}, der
   * den Vergleich stillschweigend überspringt, weil es keine gemeinsame Prüfsumme gibt, wäre kein
   * Nachweis.
   */
  @Test
  void derUmzugPrueftEinseitigUndByteweise() throws IOException {
    assertThat(wirksameZeilenMit(UMZUG_OVERLAY, "rclone check"))
        .as("Prüfschritt in %s", UMZUG_OVERLAY)
        .hasSize(1)
        .allSatisfy(
            zeile -> {
              assertThat(zeile).as("verwaiste Objekte im Ziel sind erlaubt").contains("--one-way");
              assertThat(zeile).as("byteweiser Vergleich statt Prüfsummen").contains("--download");
            });
    assertThat(wirksameZeilenMit(UMZUG_OVERLAY, "rclone sync"))
        .as("%s kopiert mit copy, nicht mit sync — sync löschte auf der Zielseite", UMZUG_OVERLAY)
        .isEmpty();
    assertThat(wirksameZeilenMit(UMZUG_OVERLAY, "rclone copy"))
        .as("Kopierschritt in %s", UMZUG_OVERLAY)
        .hasSize(1);
  }

  /**
   * Kein Zugangsdatenwert im Klartext (Akzeptanzkriterium): Jede Zeile, die Zugangsdaten trägt,
   * nennt einen Variablenverweis. Auch ein {@code :-}-Standardwert zählt als Klartext — er ließe
   * den Umzug mit den im öffentlichen Repository stehenden Werten laufen, ohne dass etwas anschlägt
   * (dieselbe Lücke wie in Issue #1227).
   */
  @Test
  void dasUmzugsOverlayNenntKeinenZugangsdatenwertImKlartext() throws IOException {
    List<String> zugangszeilen =
        wirksameZeilen(UMZUG_OVERLAY).stream()
            .filter(zeile -> zeile.contains("ACCESS_KEY") || zeile.contains("SECRET"))
            .toList();

    assertThat(zugangszeilen)
        .as("Zugangsdaten-Zeilen in %s — ein leerer Treffer wäre kein Beweis", UMZUG_OVERLAY)
        .isNotEmpty();
    assertThat(zugangszeilen)
        .as("jede Zugangsdaten-Zeile in %s ist ein reiner Variablenverweis", UMZUG_OVERLAY)
        .allSatisfy(
            zeile -> {
              assertThat(zeile).contains("${");
              assertThat(zeile).doesNotContain(":-");
            });

    String overlay = Files.readString(UMZUG_OVERLAY, StandardCharsets.UTF_8);
    assertThat(OEFFENTLICH_BEKANNTE_WERTE)
        .allSatisfy(
            wert ->
                assertThat(overlay)
                    .as(
                        "%s darf den öffentlich bekannten Wert %s nicht nennen",
                        UMZUG_OVERLAY, wert)
                    .doesNotContain(wert));
  }

  /**
   * Release 1 bringt den neuen Speicherdienst auf den Server, <b>ohne</b> die Anwendung
   * umzuschalten: Sonst wäre das Wartungsfenster die gesamte Kopierzeit, und die Vorkopie im
   * laufenden Betrieb hätte keinen Zweck. Dasselbe gilt für die Sicherung — spiegelte sie in dieser
   * Zeit den neuen, noch leeren Speicher, blieben die Anhänge, die währenddessen entstehen,
   * ungesichert.
   */
  @Test
  void dasUmzugsOverlayHaeltAnwendungUndSicherungBeiDerAltenAdresse() throws IOException {
    List<String> endpunkte =
        wirksameZeilen(UMZUG_OVERLAY).stream()
            .filter(zeile -> zeile.startsWith("MANBAN_STORAGE_ENDPOINT:"))
            .toList();

    assertThat(endpunkte)
        .as("die Anwendung liest in %s weiter die alte Adresse", UMZUG_OVERLAY)
        .hasSize(1);
    assertThat(endpunkte.getFirst()).contains(ALTER_DIENSTNAME);

    assertThat(wirksameZeilenMit(UMZUG_OVERLAY, "RCLONE_CONFIG_SPEICHER_ENDPOINT:"))
        .as("auch der Anhang-Spiegel bleibt in %s bei der alten Adresse", UMZUG_OVERLAY)
        .hasSize(1)
        .allSatisfy(zeile -> assertThat(zeile).contains(ALTER_DIENSTNAME));
  }

  /** Wie beim Rückweg-Overlay: Ein temporäres Overlay sagt in seinem Kopf, wofür es gut ist. */
  @Test
  void dasUmzugsOverlayBeginntMitEinemKopfkommentar() throws IOException {
    assertThat(Files.readAllLines(UMZUG_OVERLAY, StandardCharsets.UTF_8).getFirst())
        .as("%s beginnt mit einem Kopfkommentar", UMZUG_OVERLAY)
        .startsWith("#");
  }

  /** Bindet {@link #ALTER_DIENSTNAME} an das Overlay, aus dem der Umzug liest. */
  @Test
  void dasRueckwegOverlayFuehrtDenAltenDienst() throws IOException {
    assertThat(wirksameZeilen(ALTSPEICHER_OVERLAY))
        .as("Dienst %s in %s", ALTER_DIENSTNAME, ALTSPEICHER_OVERLAY)
        .contains(ALTER_DIENSTNAME + ":");
  }

  /**
   * Der alte Speicher startet mit genau den Zugangsdaten, mit denen der Umzug aus ihm liest (Issue
   * #1233). Las er stattdessen {@code MINIO_ROOT_*} mit Standardwert, fiel er nach der in der
   * Anleitung beschriebenen Umbenennung still auf {@code manban}/{@code manban-minio} zurück — die
   * Vorkopie las dann mit den bisherigen Werten gegen einen Container, der sie nicht mehr kennt.
   * Ohne Standardwert scheitert ein fehlender Wert schon an der {@code config}-Probe.
   */
  @Test
  void derAlteSpeicherStartetMitDenZugangsdatenDesUmzugs() throws IOException {
    List<String> zeilen = wirksameZeilen(ALTSPEICHER_OVERLAY);

    assertThat(zeilen)
        .as("Kennung des alten Speichers in %s", ALTSPEICHER_OVERLAY)
        .anySatisfy(
            zeile -> assertThat(zeile).startsWith("MINIO_ROOT_USER: ${UMZUG_ALT_ACCESS_KEY:?"));
    assertThat(zeilen)
        .as("Geheimnis des alten Speichers in %s", ALTSPEICHER_OVERLAY)
        .anySatisfy(
            zeile -> assertThat(zeile).startsWith("MINIO_ROOT_PASSWORD: ${UMZUG_ALT_SECRET_KEY:?"));
    assertThat(String.join("\n", zeilen))
        .as("%s liest keine MINIO_ROOT_*-Variable mehr", ALTSPEICHER_OVERLAY)
        .doesNotContain("${MINIO_ROOT");
  }

  @Test
  void dieAnleitungFuehrtDenAbschnittZurUmstellung() throws IOException {
    assertThat(Files.readString(BETRIEB, StandardCharsets.UTF_8))
        .as("Überschrift des Umzugs in %s", BETRIEB)
        .contains(ABSCHNITT);
  }

  /**
   * Die Zuordnungstabelle alt → neu, alle sechs Zeilen: Ein Betreiber, der einen alten Namen in
   * seiner {@code .env} stehen lässt, reicht einen Wert durch, den niemand mehr liest — die
   * Anwendung fiele still auf ihren Standardwert zurück, statt zu scheitern.
   */
  @Test
  void dieAnleitungOrdnetJedeAbgeloesteVariableIhremNachfolgerZu() throws IOException {
    List<String> abschnitt = abschnittZeilen();

    assertThat(ZUORDNUNG)
        .allSatisfy(
            (alt, neu) ->
                assertThat(abschnitt)
                    .as("Zuordnung %s → %s im Abschnitt „%s\"", alt, neu, ABSCHNITT)
                    .anySatisfy(
                        zeile -> {
                          assertThat(zeile).contains(alt);
                          assertThat(zeile).contains(neu);
                        }));
  }

  /**
   * Die beiden Releases, das Fenster und die selbsttätige Umstellung (E18). Ohne den Hinweis auf
   * den Deploy suchte jemand im Fenster nach einem Handgriff, den es nicht gibt.
   */
  @Test
  void dieAnleitungBeschreibtBeideReleasesUndDasFenster() throws IOException {
    String abschnitt = String.join("\n", abschnittZeilen());

    assertThat(abschnitt)
        .as("die zwei Stufen des Umzugs im Abschnitt „%s\"", ABSCHNITT)
        .contains("Release 1")
        .contains("Release 2");
    assertThat(abschnitt).as("Länge des Wartungsfensters").contains("30 Minuten");
    assertThat(abschnitt).as("der Umstieg ist der Deploy selbst (E18)").contains("production");
    assertThat(abschnitt)
        .as("der Einmal-Dienst, der die Anhänge kopiert")
        .contains("docker-compose.umzug.yml");
  }

  /**
   * Rückweg (E13/E18) und Ersatzweg (E14). Der Rückweg läuft über einen Revert auf {@code
   * production} — der nächste Deploy stellt den vorigen Stand her — plus das Altspeicher-Overlay.
   * Der Ersatzweg greift, wenn das alte Abbild im lokalen Vorrat des Servers fehlt: Genau dieser
   * Vorrat ist das Einzige, was die Produktion heute noch trägt.
   */
  @Test
  void dieAnleitungFuehrtRueckwegUndErsatzweg() throws IOException {
    String abschnitt = String.join("\n", abschnittZeilen());

    assertThat(abschnitt)
        .as("Rückweg über Revert auf %s", "production")
        .contains("Revert")
        .contains(ALTSPEICHER_OVERLAY.toString());
    assertThat(abschnitt)
        .as("das alte Volume bleibt eine Version lang unangetastet")
        .contains("minio_data");
    assertThat(abschnitt)
        .as("Ersatzweg über Spiegel beziehungsweise Sicherung (E14)")
        .contains("restore.sh");
    assertThat(abschnitt)
        .as("Vollständigkeitsnachweis über den Abgleich, verwaiste Objekte bleiben erlaubt")
        .contains("reconciliation");
  }

  /** Die Zeilen des Umzugs-Abschnitts — von seiner Überschrift bis zur nächsten auf Ebene zwei. */
  private static List<String> abschnittZeilen() throws IOException {
    List<String> alle = Files.readAllLines(BETRIEB, StandardCharsets.UTF_8);
    int start = alle.indexOf(ABSCHNITT);
    assertThat(start).as("Abschnitt „%s\" in %s", ABSCHNITT, BETRIEB).isNotNegative();

    List<String> abschnitt = new ArrayList<>();
    for (String zeile : alle.subList(start + 1, alle.size())) {
      if (zeile.startsWith("## ")) {
        break;
      }
      abschnitt.add(zeile);
    }
    return abschnitt;
  }

  /** Getrimmte Zeilen ohne Kommentare — nur der wirksame Eintrag zählt. */
  private static List<String> wirksameZeilen(Path datei) throws IOException {
    return Files.readAllLines(datei, StandardCharsets.UTF_8).stream()
        .map(String::trim)
        .filter(zeile -> !zeile.isBlank() && !zeile.startsWith("#"))
        .toList();
  }

  private static List<String> wirksameZeilenMit(Path datei, String fragment) throws IOException {
    return wirksameZeilen(datei).stream().filter(zeile -> zeile.contains(fragment)).toList();
  }
}
