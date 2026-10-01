package org.mwolff.manban.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

/**
 * Die Startprüfung des Datenbankkennworts (Issue #1266, Plan #1262, fachlich #675).
 *
 * <p>Geprüft werden dieselben drei beobachtbaren Ergebnisse wie bei {@code
 * ObjectStorageStartupCheck}: Ausnahme (unsicher im Produktivbetrieb), Warnung (unsicher im
 * ausdrücklich eingeschalteten Entwicklungs-/Testbetrieb) und Schweigen (eigenes Kennwort). Jede
 * Bedingung steht in einem eigenen Fall — fehlend, leer, nur Leerraum und je abgelehnter
 * Vorgabewert —, damit die Mutationsschwelle 100 % für den Bereich {@code backend} hält
 * (CLAUDE-java.md §5).
 */
class DatabasePasswordStartupCheckTest {

  /**
   * Die abgelehnten Werte als eigene Literale — bewusst <em>nicht</em> als Verweis auf die
   * Konstanten der Prüfklasse: Erst so schreibt der Test unabhängig fest, welche konkreten Werte
   * als unsicher gelten. Gegen dieselbe Konstante zu prüfen wäre tautologisch — wer einen Wert
   * stillschweigend umschriebe, bliebe grün.
   *
   * <p>Vorgabe aus {@code application.yml} ({@code ${MANBAN_DB_PASSWORD:manban}}) und der {@code
   * :-manban}-Vorgabe von {@code POSTGRES_PASSWORD} in {@code docker-compose.yml}.
   */
  private static final String ABGELEHNT_VORGABE = "manban";

  /**
   * Platzhalter von {@code POSTGRES_PASSWORD} in {@code .env.example}. Er steht dort, weil die
   * Vorlage bewusst nicht lauffähig sein soll — genau darum muss ihn auch die Startprüfung
   * ablehnen, sonst startete eine unveränderte Kopie der Vorlage mit einem öffentlich bekannten
   * Kennwort.
   */
  private static final String ABGELEHNT_ENV_VORLAGE = "change-me";

  /** Bewusst gesetztes eigenes Kennwort — nie Teil einer Ausgabe. */
  private static final String EIGENES_KENNWORT = "kennwort-9b3f61a0d47c28e5031ac5d9e7b204f18";

  /** Umgebungsvariable, mit der ein Betreiber das Kennwort setzt. */
  private static final String VARIABLE_SPEICHERDIENST = "POSTGRES_PASSWORD";

  /** Umgebungsvariable, über die die Anwendung dasselbe Kennwort liest. */
  private static final String VARIABLE_ANWENDUNG = "MANBAN_DB_PASSWORD";

  /** Property, unter der Spring das Kennwort führt. */
  private static final String PROPERTY = "spring.datasource.password";

  /**
   * Fängt die Log-Ausgabe der Prüf-Bean. Der Logger ist global — deshalb im {@code @BeforeEach}
   * anmelden und im {@code @AfterEach} wieder abmelden, sonst sammelte der Appender über
   * Testklassen hinweg weiter.
   */
  private final ListAppender<ILoggingEvent> logWatcher = new ListAppender<>();

  @BeforeEach
  void watchStartupCheckLog() {
    logWatcher.start();
    ((Logger) LoggerFactory.getLogger(DatabasePasswordStartupCheck.class)).addAppender(logWatcher);
  }

  @AfterEach
  void unwatchStartupCheckLog() {
    ((Logger) LoggerFactory.getLogger(DatabasePasswordStartupCheck.class))
        .detachAppender(logWatcher);
  }

  /**
   * Jede einzelne Bedingung in einem eigenen Fall: fehlend, leer, nur Leerraum und jeder abgelehnte
   * Vorgabewert.
   */
  static Stream<Arguments> abgelehnteKennwoerter() {
    return Stream.of(
        Arguments.of("Kennwort fehlt", null),
        Arguments.of("Kennwort leer", ""),
        Arguments.of("Kennwort nur Leerraum", "   "),
        Arguments.of(
            "Kennwort auf der Vorgabe der Anwendung und der Composition", ABGELEHNT_VORGABE),
        Arguments.of("Kennwort auf dem Platzhalter der .env-Vorlage", ABGELEHNT_ENV_VORLAGE));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("abgelehnteKennwoerter")
  void abgelehntesKennwort_ohneEntwicklungsbetrieb_verweigertDenStartUndMeldetError(
      String fall, String kennwort) {
    // When / Then
    assertThatThrownBy(() -> new DatabasePasswordStartupCheck(kennwort, false))
        .as(fall)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(VARIABLE_SPEICHERDIENST)
        .hasMessageContaining(VARIABLE_ANWENDUNG)
        .hasMessageContaining(PROPERTY)
        .hasMessageContaining("manban.dev-mode=true");

    assertThat(meldungen(Level.ERROR)).hasSize(1);
    assertThat(meldungen(Level.ERROR).getFirst())
        .contains(VARIABLE_SPEICHERDIENST)
        .contains(VARIABLE_ANWENDUNG)
        .contains(PROPERTY)
        .contains("manban.dev-mode=true");
    assertThat(meldungen(Level.WARN)).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("abgelehnteKennwoerter")
  void abgelehntesKennwort_imEntwicklungsbetrieb_laesstDenStartZuUndWarnt(
      String fall, String kennwort) {
    // When
    new DatabasePasswordStartupCheck(kennwort, true);

    // Then
    assertThat(meldungen(Level.WARN)).as(fall).hasSize(1);
    assertThat(meldungen(Level.WARN).getFirst())
        .contains(VARIABLE_SPEICHERDIENST)
        .contains(VARIABLE_ANWENDUNG)
        .contains(PROPERTY);
    assertThat(meldungen(Level.ERROR)).isEmpty();
  }

  @Test
  void eigenesKennwort_ohneEntwicklungsbetrieb_schweigt() {
    // When
    new DatabasePasswordStartupCheck(EIGENES_KENNWORT, false);

    // Then
    assertThat(logWatcher.list).isEmpty();
  }

  @Test
  void eigenesKennwort_imEntwicklungsbetrieb_schweigt() {
    // When
    new DatabasePasswordStartupCheck(EIGENES_KENNWORT, true);

    // Then
    assertThat(logWatcher.list).isEmpty();
  }

  /**
   * Kein Kennwortwert verlässt die Klasse — auch nicht gekürzt oder als Prüfsumme. Geprüft an
   * Ausnahmemeldung, ERROR- und WARN-Zeile.
   *
   * <p>Der abgelehnte Vorgabewert {@code manban} bleibt außen vor: Er steckt als Namensbestandteil
   * in {@code manban.dev-mode} und in {@code MANBAN_DB_PASSWORD}, eine Prüfung darauf könnte nie
   * halten. Er trägt auch keinen Geheimnisanteil — {@code manban} ist zugleich Datenbank- und
   * Rollenname und steht offen in {@code docker-compose.yml}. Der Platzhalter {@code change-me} und
   * ein eigenes Kennwort tragen einen: beide werden geprüft.
   */
  @Test
  void keineAusgabeNenntEinenKennwortwert() {
    // Given — ein gesetztes eigenes Kennwort kann die Prüfung nicht auslösen; sie schlägt am
    // Platzhalter an, und derselbe Lauf belegt zugleich, dass auch der Platzhalter nicht in der
    // Ausgabe landet.
    String ausnahmeMeldung =
        catchThrowable(() -> new DatabasePasswordStartupCheck(ABGELEHNT_ENV_VORLAGE, false))
            .getMessage();

    // When
    new DatabasePasswordStartupCheck(ABGELEHNT_ENV_VORLAGE, true);

    // Then
    assertThat(ausnahmeMeldung).doesNotContain(ABGELEHNT_ENV_VORLAGE);
    assertThat(meldungen(Level.ERROR)).hasSize(1);
    assertThat(meldungen(Level.WARN)).hasSize(1);
    assertThat(logWatcher.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
        .allSatisfy(meldung -> assertThat(meldung).doesNotContain(ABGELEHNT_ENV_VORLAGE));
  }

  /** Vergleich über {@code levelInt}: {@link Level} überschreibt {@code equals} nicht. */
  private List<String> meldungen(Level level) {
    return logWatcher.list.stream()
        .filter(event -> event.getLevel().levelInt == level.levelInt)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }
}
