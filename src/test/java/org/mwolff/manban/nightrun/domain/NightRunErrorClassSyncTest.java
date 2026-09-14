package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Hält die drei Orte gleich, an denen die Wertelisten und die Auszugsgrenze der
 * Nachtlauf-Auswertung stehen: der Parser {@code frontend/src/lib/nightRunLog.ts}, die Java-Typen
 * dieses Packages und die Flyway-Migrationen (Issue #721).
 *
 * <p>Verbindlich ist der Parser (Plan #718, A13) — der Java-Typ spiegelt ihn. Bewusst ein
 * JUnit-Test nach dem Muster von {@code RightsDocumentationTest} und <strong>kein</strong>
 * Vitest-Test: {@code mvn verify} führt {@code npm run build} aus, nicht {@code npm test}; in
 * Vitest liefe die Leitplanke am Pflichtcheck vorbei.
 *
 * <p><b>Die Wertelisten kommen aus dem gesamten Migrationsverzeichnis, nicht aus einer einzelnen
 * Datei</b> (Issue #853): Ein {@code CHECK} wird über die Zeit neu gesetzt — {@code V30} ersetzt
 * {@code ck_night_run_mode} aus {@code V29}. Wer nur die erste Datei läse, sähe den abgelösten
 * Stand und hielte einen neuen Wert für fehlend. Gelesen wird deshalb in Flyway-Reihenfolge und
 * gewertet wird die <b>zuletzt</b> definierte Liste — also die, die auf der migrierten Datenbank
 * wirkt.
 */
class NightRunErrorClassSyncTest {

  /** Verbindliche Fassung der Wertelisten (Plan #718, A13). */
  private static final Path PARSER = Path.of("frontend", "src", "lib", "nightRunLog.ts");

  /** Alle Flyway-Migrationen — irgendeine davon trägt den jeweils wirksamen {@code CHECK}. */
  private static final Path MIGRATIONEN = Path.of("src", "main", "resources", "db", "migration");

  /**
   * Die Migration, die die Spalten <em>anlegt</em>. Die Längenprüfung gehört hierher und nicht ins
   * Verzeichnis: {@code varchar(n)} steht in der {@code CREATE TABLE}, und keine spätere Migration
   * ändert sie — eine, die es täte, müsste diese Konstante mitnehmen.
   */
  private static final Path SPALTEN_MIGRATION = MIGRATIONEN.resolve("V29__night_run.sql");

  /** Die Einträge des {@code NIGHT_RUN_ERROR_CLASSES}-Arrays im Parser. */
  private static final Pattern TS_FEHLERKLASSEN =
      Pattern.compile("NIGHT_RUN_ERROR_CLASSES\\s*=\\s*\\[(.*?)]", Pattern.DOTALL);

  /** Der Zahlwert des {@code NIGHT_RUN_EXCERPT_MAX}-Exports im Parser. */
  private static final Pattern TS_AUSZUGSGRENZE =
      Pattern.compile("NIGHT_RUN_EXCERPT_MAX\\s*=\\s*(\\d+)");

  /** Ein einzelner Zeichenkettenwert innerhalb einer TS- oder SQL-Liste. */
  private static final Pattern TS_WERT = Pattern.compile("'([A-Z_]+)'");

  /** Die Versionsnummer einer Migration — {@code V30__night_run_kette.sql} ergibt {@code 30}. */
  private static final Pattern MIGRATIONS_VERSION = Pattern.compile("^V(\\d+)__");

  @Test
  void javaTypSpiegeltDieFehlerklassenDesParsers() throws IOException {
    List<String> ausDemParser = fehlerklassenAusParser();

    List<String> ausJava = Arrays.stream(NightRunErrorClass.values()).map(Enum::name).toList();

    assertThat(ausJava)
        .as("Fehlerklassen in %s (verbindlich, Plan #718 A13)", PARSER)
        .containsExactlyInAnyOrderElementsOf(ausDemParser);
  }

  @Test
  void dieAchtFehlerklassenDesPlansSindAbgedeckt() throws IOException {
    assertThat(fehlerklassenAusParser())
        .as("abgeschlossene Liste aus Plan #718, A13; die achte kommt aus #842")
        .containsExactlyInAnyOrder(
            "CHECKS_RED",
            "CHECKS_NOT_STARTED",
            "DEPENDENCY_UNMET",
            "UNEXPECTED_STATE",
            "HARD_ABORT",
            "AWAITING_DECISION",
            "REVIEWER_FAILED",
            "TIME_BUDGET_EXCEEDED");
  }

  @Test
  void jedeFehlerklasseStehtImWirksamenCheck() {
    List<String> imCheck = wirksameWerte("ck_night_run_item_error_class");

    List<String> fehlend =
        Arrays.stream(NightRunErrorClass.values())
            .map(Enum::name)
            .filter(wert -> !imCheck.contains(wert))
            .toList();

    assertThat(fehlend)
        .as("Fehlerklassen ohne CHECK-Wert in der zuletzt gültigen Fassung aus %s", MIGRATIONEN)
        .isEmpty();
  }

  @Test
  void jederZustandUndJederModusStehtImWirksamenCheck() {
    List<String> zustaende = wirksameWerte("ck_night_run_item_state");
    List<String> modi = wirksameWerte("ck_night_run_mode");

    assertThat(Arrays.stream(NightRunState.values()).map(Enum::name))
        .allMatch(zustaende::contains, "steht im wirksamen CHECK ck_night_run_item_state");
    assertThat(Arrays.stream(NightRunMode.values()).map(Enum::name))
        .allMatch(modi::contains, "steht im wirksamen CHECK ck_night_run_mode");
  }

  @Test
  void dieSpaltenTragenJedenWertDerListenInVollerLaenge() throws IOException {
    String migration = Files.readString(SPALTEN_MIGRATION, StandardCharsets.UTF_8);

    assertThat(laengsterName(NightRunMode.values()))
        .as("mode-Spaltenlänge in %s reicht für den längsten Modus", SPALTEN_MIGRATION)
        .isLessThanOrEqualTo(spaltenLaenge(migration, "mode"));
    assertThat(laengsterName(NightRunState.values()))
        .as("state-Spaltenlänge in %s reicht für den längsten Zustand", SPALTEN_MIGRATION)
        .isLessThanOrEqualTo(spaltenLaenge(migration, "state"));
    assertThat(laengsterName(NightRunErrorClass.values()))
        .as("error_class-Spaltenlänge in %s reicht für die längste Fehlerklasse", SPALTEN_MIGRATION)
        .isLessThanOrEqualTo(spaltenLaenge(migration, "error_class"));
  }

  @Test
  void dieAuszugsgrenzeIstInParserJavaUndMigrationDieselbe() throws IOException {
    String parser = Files.readString(PARSER, StandardCharsets.UTF_8);
    String migration = Files.readString(SPALTEN_MIGRATION, StandardCharsets.UTF_8);

    Matcher grenze = TS_AUSZUGSGRENZE.matcher(parser);
    assertThat(grenze.find()).as("NIGHT_RUN_EXCERPT_MAX fehlt in %s", PARSER).isTrue();

    assertThat(Integer.parseInt(grenze.group(1)))
        .as("NIGHT_RUN_EXCERPT_MAX in %s gegen NightRunLimits.EXCERPT_MAX", PARSER)
        .isEqualTo(NightRunLimits.EXCERPT_MAX);
    assertThat(spaltenLaenge(migration, "unparsed_sample"))
        .as("Spaltenlänge unparsed_sample in %s", SPALTEN_MIGRATION)
        .isEqualTo(NightRunLimits.EXCERPT_MAX);
    assertThat(spaltenLaenge(migration, "excerpt"))
        .as("Spaltenlänge excerpt in %s", SPALTEN_MIGRATION)
        .isEqualTo(NightRunLimits.EXCERPT_MAX);
  }

  /**
   * Die Werteliste, die ein benannter {@code CHECK} nach allen Migrationen trägt.
   *
   * <p>Gelesen wird das ganze Verzeichnis in Flyway-Reihenfolge; jede spätere Definition
   * überschreibt die frühere. Ein {@code DROP CONSTRAINT} bleibt dabei ohne Wirkung — es nennt den
   * Namen, aber kein {@code CHECK}, und das Muster verlangt beides; in der Praxis folgt ihm ohnehin
   * sofort das {@code ADD CONSTRAINT}, das die neue Liste setzt.
   */
  private static List<String> wirksameWerte(String constraintName) {
    Pattern check =
        Pattern.compile(
            "CONSTRAINT\\s+" + constraintName + "\\s+CHECK\\s*\\([^()]*\\(([^)]*)\\)",
            Pattern.DOTALL);

    List<String> zuletzt = List.of();
    for (Path migration : inFlywayReihenfolge()) {
      String text = lies(migration);
      Matcher treffer = check.matcher(text);
      while (treffer.find()) {
        zuletzt = werte(treffer.group(1));
      }
    }

    assertThat(zuletzt)
        .as("CHECK %s in keiner Migration unter %s", constraintName, MIGRATIONEN)
        .isNotEmpty();
    return zuletzt;
  }

  /**
   * Alle {@code V*.sql} des Verzeichnisses, aufsteigend nach Versionsnummer — wie Flyway sie fährt.
   */
  private static List<Path> inFlywayReihenfolge() {
    try (Stream<Path> dateien = Files.list(MIGRATIONEN)) {
      List<Path> gefunden = new ArrayList<>();
      dateien.filter(pfad -> version(pfad) > 0).forEach(gefunden::add);
      gefunden.sort(Comparator.comparingInt(NightRunErrorClassSyncTest::version));
      assertThat(gefunden).as("Migrationen unter %s", MIGRATIONEN).isNotEmpty();
      return gefunden;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Versionsnummer einer Migration; {@code 0} für alles, was nicht dem Namensschema folgt. */
  private static int version(Path migration) {
    Matcher treffer = MIGRATIONS_VERSION.matcher(migration.getFileName().toString());
    return treffer.find() ? Integer.parseInt(treffer.group(1)) : 0;
  }

  private static String lies(Path datei) {
    try {
      return Files.readString(datei, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static List<String> fehlerklassenAusParser() throws IOException {
    String parser = Files.readString(PARSER, StandardCharsets.UTF_8);
    Matcher liste = TS_FEHLERKLASSEN.matcher(parser);
    assertThat(liste.find()).as("NIGHT_RUN_ERROR_CLASSES fehlt in %s", PARSER).isTrue();
    return werte(liste.group(1));
  }

  /**
   * Die einfach quotierten Konstanten einer Aufzählung — in TypeScript wie in SQL dieselbe Form.
   */
  private static List<String> werte(String liste) {
    return TS_WERT.matcher(liste).results().map(treffer -> treffer.group(1)).toList();
  }

  /** Länge des längsten Konstantennamens — die Untergrenze für die zugehörige Spalte. */
  private static int laengsterName(Enum<?>... werte) {
    return Arrays.stream(werte).mapToInt(wert -> wert.name().length()).max().orElseThrow();
  }

  /** Liest {@code <spalte> varchar(n)} aus der Migration. */
  private static int spaltenLaenge(String migration, String spalte) {
    Matcher treffer =
        Pattern.compile("\\b" + spalte + "\\s+varchar\\((\\d+)\\)").matcher(migration);
    assertThat(treffer.find())
        .as("Spalte %s ohne varchar(n) in %s", spalte, SPALTEN_MIGRATION)
        .isTrue();
    return Integer.parseInt(treffer.group(1));
  }
}
