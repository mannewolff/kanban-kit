package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Hält die drei Orte gleich, an denen die Wertelisten und die Auszugsgrenze der
 * Nachtlauf-Auswertung stehen: der Parser {@code frontend/src/lib/nightRunLog.ts}, die Java-Typen
 * dieses Packages und die Migration {@code V29__night_run.sql} (Issue #721).
 *
 * <p>Verbindlich ist der Parser (Plan #718, A13) — der Java-Typ spiegelt ihn. Bewusst ein
 * JUnit-Test nach dem Muster von {@code RightsDocumentationTest} und <strong>kein</strong>
 * Vitest-Test: {@code mvn verify} führt {@code npm run build} aus, nicht {@code npm test}; in
 * Vitest liefe die Leitplanke am Pflichtcheck vorbei.
 */
class NightRunErrorClassSyncTest {

  /** Verbindliche Fassung der Wertelisten (Plan #718, A13). */
  private static final Path PARSER = Path.of("frontend", "src", "lib", "nightRunLog.ts");

  /** Die Migration, die dieselben Werte als {@code CHECK} und Spaltenlänge trägt. */
  private static final Path MIGRATION =
      Path.of("src", "main", "resources", "db", "migration", "V29__night_run.sql");

  /** Die Einträge des {@code NIGHT_RUN_ERROR_CLASSES}-Arrays im Parser. */
  private static final Pattern TS_FEHLERKLASSEN =
      Pattern.compile("NIGHT_RUN_ERROR_CLASSES\\s*=\\s*\\[(.*?)]", Pattern.DOTALL);

  /** Der Zahlwert des {@code NIGHT_RUN_EXCERPT_MAX}-Exports im Parser. */
  private static final Pattern TS_AUSZUGSGRENZE =
      Pattern.compile("NIGHT_RUN_EXCERPT_MAX\\s*=\\s*(\\d+)");

  /** Ein einzelner Zeichenkettenwert innerhalb einer TS-Liste. */
  private static final Pattern TS_WERT = Pattern.compile("'([A-Z_]+)'");

  @Test
  void javaTypSpiegeltDieFehlerklassenDesParsers() throws IOException {
    List<String> ausDemParser = fehlerklassenAusParser();

    List<String> ausJava = Arrays.stream(NightRunErrorClass.values()).map(Enum::name).toList();

    assertThat(ausJava)
        .as("Fehlerklassen in %s (verbindlich, Plan #718 A13)", PARSER)
        .containsExactlyInAnyOrderElementsOf(ausDemParser);
  }

  @Test
  void dieSiebenFehlerklassenDesPlansSindAbgedeckt() throws IOException {
    assertThat(fehlerklassenAusParser())
        .as("abgeschlossene Liste aus Plan #718, A13")
        .containsExactlyInAnyOrder(
            "CHECKS_RED",
            "CHECKS_NOT_STARTED",
            "DEPENDENCY_UNMET",
            "UNEXPECTED_STATE",
            "HARD_ABORT",
            "AWAITING_DECISION",
            "REVIEWER_FAILED");
  }

  @Test
  void jedeFehlerklasseStehtImCheckDerMigration() throws IOException {
    String migration = Files.readString(MIGRATION, StandardCharsets.UTF_8);

    List<String> fehlend =
        Arrays.stream(NightRunErrorClass.values())
            .map(Enum::name)
            .filter(wert -> !migration.contains("'" + wert + "'"))
            .toList();

    assertThat(fehlend).as("Fehlerklassen ohne CHECK-Wert in %s", MIGRATION).isEmpty();
  }

  @Test
  void jederZustandUndJederModusStehtImCheckDerMigration() throws IOException {
    String migration = Files.readString(MIGRATION, StandardCharsets.UTF_8);

    assertThat(Arrays.stream(NightRunState.values()).map(Enum::name))
        .allMatch(
            wert -> migration.contains("'" + wert + "'"), "steht als CHECK-Wert in " + MIGRATION);
    assertThat(Arrays.stream(NightRunMode.values()).map(Enum::name))
        .allMatch(
            wert -> migration.contains("'" + wert + "'"), "steht als CHECK-Wert in " + MIGRATION);
  }

  @Test
  void dieSpaltenTragenJedenWertDerListenInVollerLaenge() throws IOException {
    String migration = Files.readString(MIGRATION, StandardCharsets.UTF_8);

    assertThat(laengsterName(NightRunMode.values()))
        .as("mode-Spaltenlänge in %s reicht für den längsten Modus", MIGRATION)
        .isLessThanOrEqualTo(spaltenLaenge(migration, "mode"));
    assertThat(laengsterName(NightRunState.values()))
        .as("state-Spaltenlänge in %s reicht für den längsten Zustand", MIGRATION)
        .isLessThanOrEqualTo(spaltenLaenge(migration, "state"));
    assertThat(laengsterName(NightRunErrorClass.values()))
        .as("error_class-Spaltenlänge in %s reicht für die längste Fehlerklasse", MIGRATION)
        .isLessThanOrEqualTo(spaltenLaenge(migration, "error_class"));
  }

  @Test
  void dieAuszugsgrenzeIstInParserJavaUndMigrationDieselbe() throws IOException {
    String parser = Files.readString(PARSER, StandardCharsets.UTF_8);
    String migration = Files.readString(MIGRATION, StandardCharsets.UTF_8);

    Matcher grenze = TS_AUSZUGSGRENZE.matcher(parser);
    assertThat(grenze.find()).as("NIGHT_RUN_EXCERPT_MAX fehlt in %s", PARSER).isTrue();

    assertThat(Integer.parseInt(grenze.group(1)))
        .as("NIGHT_RUN_EXCERPT_MAX in %s gegen NightRunLimits.EXCERPT_MAX", PARSER)
        .isEqualTo(NightRunLimits.EXCERPT_MAX);
    assertThat(spaltenLaenge(migration, "unparsed_sample"))
        .as("Spaltenlänge unparsed_sample in %s", MIGRATION)
        .isEqualTo(NightRunLimits.EXCERPT_MAX);
    assertThat(spaltenLaenge(migration, "excerpt"))
        .as("Spaltenlänge excerpt in %s", MIGRATION)
        .isEqualTo(NightRunLimits.EXCERPT_MAX);
  }

  private static List<String> fehlerklassenAusParser() throws IOException {
    String parser = Files.readString(PARSER, StandardCharsets.UTF_8);
    Matcher liste = TS_FEHLERKLASSEN.matcher(parser);
    assertThat(liste.find()).as("NIGHT_RUN_ERROR_CLASSES fehlt in %s", PARSER).isTrue();
    return TS_WERT.matcher(liste.group(1)).results().map(treffer -> treffer.group(1)).toList();
  }

  /** Länge des längsten Konstantennamens — die Untergrenze für die zugehörige Spalte. */
  private static int laengsterName(Enum<?>... werte) {
    return Arrays.stream(werte).mapToInt(wert -> wert.name().length()).max().orElseThrow();
  }

  /** Liest {@code <spalte> varchar(n)} aus der Migration. */
  private static int spaltenLaenge(String migration, String spalte) {
    Matcher treffer =
        Pattern.compile("\\b" + spalte + "\\s+varchar\\((\\d+)\\)").matcher(migration);
    assertThat(treffer.find()).as("Spalte %s ohne varchar(n) in %s", spalte, MIGRATION).isTrue();
    return Integer.parseInt(treffer.group(1));
  }
}
