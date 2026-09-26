package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hält die ausgelieferten Betriebsdateien an den Zusicherungen fest, die ein Betreiber ihnen
 * entnimmt: Das Produktions-Overlay schaltet den Entwicklungs-Schalter fest aus, die
 * Umgebungs-Vorlage liefert keinen funktionierenden Sitzungsschlüssel mit (Issue #889), das
 * automatische Deployment startet den Stack mit dem Sicherungs-Overlay (Issue #1198), und der
 * ausgelieferte Stack fährt kein Abbild, das anonym nicht mehr beziehbar ist (Issue #1226).
 *
 * <p>Das Sicherungs-Overlay {@code docker-compose.backup.yml} ist der einzige Schalter der
 * Sicherung: Fehlt es in einem Compose-Aufruf des Deploy-Workflows, legt der Deploy {@code
 * postgres} ohne WAL-Archivierung und {@code manban-api} ohne {@code MANBAN_BACKUP_ENABLED} neu an
 * — die Sicherung fällt stumm aus (Plan #825, E6).
 *
 * <p>Der Schalter {@code MANBAN_DEV_MODE} steht im Overlay bewusst als fester Wert und <b>nicht</b>
 * als {@code ${…}}-Interpolation: Ein Wert aus der {@code .env} wäre dieselbe Lücke mit einem
 * Zwischenschritt — wer {@code MANBAN_DEV_MODE=true} für den lokalen Betrieb in seine {@code .env}
 * schreibt, nähme ihn in den Produktionslauf mit.
 *
 * <p>Geprüft wird der Dateitext selbst, nicht die zusammengeführte Compose-Konfiguration: Der Test
 * soll ohne laufenden Docker-Daemon greifen. Ablage im Wurzelpaket neben {@link
 * RightsDocumentationTest} aus demselben Grund wie dort — geprüft werden ausgelieferte
 * Repository-Dateien, keine Fachlogik eines Moduls.
 */
class BetriebsdateienTest {

  /** Produktions-Overlay über dem lokalen Basis-Stack {@code docker-compose.yml}. */
  private static final Path PROD_OVERLAY = Path.of("docker-compose.prod.yml");

  /** Vorlage, die ein Betreiber nach {@code .env} kopiert. */
  private static final Path UMGEBUNGS_VORLAGE = Path.of(".env.example");

  /** Workflow, der bei jedem Push auf {@code production} den Stack neu startet. */
  private static final Path DEPLOY_WORKFLOW = Path.of(".github/workflows/deploy.yml");

  /** Lokaler Basis-Stack. */
  private static final Path BASIS_STACK = Path.of("docker-compose.yml");

  /** Rückweg-Overlay mit dem alten Speicherdienst (Plan #1222, E13). */
  private static final Path ALTSPEICHER_OVERLAY = Path.of("docker-compose.altspeicher.yml");

  /** Die Anwendungskonfiguration, die die Speicher-Variablen ausliest. */
  private static final Path ANWENDUNGS_KONFIGURATION =
      Path.of("src/main/resources/application.yml");

  /** Abbild, das anonym nicht mehr beziehbar ist (Plan #1222, geprüft am 2026-09-26). */
  private static final String NICHT_BEZIEHBARES_ABBILD = "quay.io/minio/minio";

  /** Präfix der abgelösten Speicher-Variablen (Plan #1222, E6). */
  private static final String ALTES_VARIABLEN_PRAEFIX = "MANBAN_MINIO_";

  /** Einziger Schalter der Sicherung — siehe Klassen-Javadoc. */
  private static final String SICHERUNGS_OVERLAY = "-f docker-compose.backup.yml";

  /** Standardwert des lokalen Stacks — in der Vorlage wäre er ein gesetzter Schlüssel. */
  private static final String UNSICHERER_STANDARDWERT = "dev-only-insecure-secret-change-me";

  @Test
  void produktionsOverlaySchaltetDenEntwicklungsSchalterFestAus() throws IOException {
    List<String> schalterZeilen = wirksameZeilenMit(PROD_OVERLAY, "MANBAN_DEV_MODE");

    assertThat(schalterZeilen)
        .as("Entwicklungs-Schalter in %s — fester Wert, keine ${…}-Interpolation", PROD_OVERLAY)
        .containsExactly("MANBAN_DEV_MODE: \"false\"");
  }

  @Test
  void umgebungsVorlageLiefertKeinenSitzungsschluesselMit() throws IOException {
    List<String> schluesselZeilen = wirksameZeilenMit(UMGEBUNGS_VORLAGE, "MANBAN_SESSION_SECRET=");

    assertThat(schluesselZeilen)
        .as("Sitzungsschlüssel in %s — Zeile ohne Wert", UMGEBUNGS_VORLAGE)
        .containsExactly("MANBAN_SESSION_SECRET=");
  }

  @Test
  void umgebungsVorlageNenntDenUnsicherenStandardwertNirgends() throws IOException {
    String vorlage = Files.readString(UMGEBUNGS_VORLAGE, StandardCharsets.UTF_8);

    assertThat(vorlage)
        .as("%s darf den unsicheren Standardwert auch nicht im Fließtext nennen", UMGEBUNGS_VORLAGE)
        .doesNotContain(UNSICHERER_STANDARDWERT);
  }

  @Test
  void deployWorkflowGibtJedemComposeAufrufDasSicherungsOverlayMit() throws IOException {
    List<String> composeAufrufe = wirksameZeilenMit(DEPLOY_WORKFLOW, "docker compose");

    assertThat(composeAufrufe)
        .as("Compose-Aufrufe in %s — ein leerer Treffer wäre kein Beweis", DEPLOY_WORKFLOW)
        .isNotEmpty();
    assertThat(composeAufrufe)
        .as("jeder Compose-Aufruf in %s trägt das Sicherungs-Overlay", DEPLOY_WORKFLOW)
        .allSatisfy(zeile -> assertThat(zeile).contains(SICHERUNGS_OVERLAY));
  }

  /**
   * Das MinIO-Abbild ist anonym nicht mehr beziehbar (Plan #1222): Ein Stack, der es fährt, kommt
   * auf einer Maschine ohne Anmeldung an einer Registry nicht hoch. Es darf darum nur noch im
   * Rückweg-Overlay stehen, das ausdrücklich aus dem lokalen Vorrat des Servers lebt (E13) — und
   * dort muss ein Kopfkommentar sagen, wofür es gut ist.
   */
  @Test
  void nurDasRueckwegOverlayFaehrtNochDasNichtBeziehbareAbbild() throws IOException {
    assertThat(wirksameZeilenMit(BASIS_STACK, NICHT_BEZIEHBARES_ABBILD))
        .as("%s darf %s nicht mehr fahren", BASIS_STACK, NICHT_BEZIEHBARES_ABBILD)
        .isEmpty();
    assertThat(wirksameZeilenMit(PROD_OVERLAY, NICHT_BEZIEHBARES_ABBILD))
        .as("%s darf %s nicht mehr fahren", PROD_OVERLAY, NICHT_BEZIEHBARES_ABBILD)
        .isEmpty();

    assertThat(wirksameZeilenMit(ALTSPEICHER_OVERLAY, NICHT_BEZIEHBARES_ABBILD))
        .as("%s ist der Rückweg und behält das alte Abbild", ALTSPEICHER_OVERLAY)
        .isNotEmpty();
    assertThat(Files.readAllLines(ALTSPEICHER_OVERLAY, StandardCharsets.UTF_8).getFirst())
        .as("%s beginnt mit einem Kopfkommentar", ALTSPEICHER_OVERLAY)
        .startsWith("#");
  }

  /**
   * Die Anwendung liest ihre Speicher-Zugangsdaten unter {@code MANBAN_STORAGE_*} (Plan #1222, E6).
   * Bliebe ein alter Name in einer der ausgelieferten Dateien stehen, liefe der Wert ins Leere:
   * Compose reichte ihn durch, die Anwendung läse ihn nicht mehr — und griffe still auf ihren
   * Standardwert zurück, statt zu scheitern.
   */
  @Test
  void keineAusgelieferteBetriebsdateiNenntDieAbgeloestenSpeicherVariablen() throws IOException {
    for (Path datei :
        List.of(BASIS_STACK, PROD_OVERLAY, UMGEBUNGS_VORLAGE, ANWENDUNGS_KONFIGURATION)) {
      assertThat(wirksameZeilenMit(datei, ALTES_VARIABLEN_PRAEFIX))
          .as("abgelöste Speicher-Variablen in %s", datei)
          .isEmpty();
    }
  }

  /**
   * Getrimmte Zeilen der Datei, die {@code fragment} tragen — ohne Kommentarzeilen, damit die
   * Erläuterungen über einem Eintrag frei formuliert bleiben und nur der wirksame Eintrag zählt.
   */
  private static List<String> wirksameZeilenMit(Path datei, String fragment) throws IOException {
    return Files.readAllLines(datei, StandardCharsets.UTF_8).stream()
        .map(String::trim)
        .filter(zeile -> !zeile.startsWith("#"))
        .filter(zeile -> zeile.contains(fragment))
        .toList();
  }
}
