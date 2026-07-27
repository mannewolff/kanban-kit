package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
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
 */
class RightsDocumentationTest {

  /**
   * Einzige Rechte-Übersicht im Repository (wird über VitePress unter {@code /docs/}
   * bereitgestellt).
   */
  private static final Path RECHTE_UEBERSICHT = Path.of("docs", "rollen-und-rechte.md");

  /** Die Wurzel-Datei war die zweite, driftende Kopie derselben Übersicht (Issue #437). */
  private static final Path ENTFERNTE_KOPIE = Path.of("rollen_rechte.md");

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
  void esGibtNurEineRechteUebersicht() {
    assertThat(ENTFERNTE_KOPIE)
        .as("Zweite Rechte-Übersicht — Quelle ist %s", RECHTE_UEBERSICHT)
        .doesNotExist();
  }
}
