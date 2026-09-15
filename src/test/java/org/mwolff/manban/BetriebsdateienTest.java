package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hält die ausgelieferten Betriebsdateien an den beiden Zusicherungen fest, die ein Betreiber ihnen
 * entnimmt: Das Produktions-Overlay schaltet den Entwicklungs-Schalter fest aus, und die
 * Umgebungs-Vorlage liefert keinen funktionierenden Sitzungsschlüssel mit (Issue #889).
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
