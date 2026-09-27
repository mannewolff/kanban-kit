package org.mwolff.manban.attachment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import org.mwolff.manban.attachment.application.ObjectStorageProperties;
import org.slf4j.LoggerFactory;

/**
 * Die Startprüfung des Objektspeicher-Zugangs (Issue #1227, Plan #1222, fachlich #1221).
 *
 * <p>Geprüft werden dieselben drei beobachtbaren Ergebnisse wie bei {@code
 * SessionSecretStartupCheck}: Ausnahme (unsicher im Produktivbetrieb), Warnung (unsicher im
 * ausdrücklich eingeschalteten Entwicklungs-/Testbetrieb) und Schweigen (sicher). Jede Bedingung
 * steht in einem eigenen Fall — je Feld fehlend, leer und je mitgelieferter Standardwert —, damit
 * die Mutationsschwelle 100 % für den Bereich {@code backend} hält (CLAUDE-java.md §5).
 */
class ObjectStorageStartupCheckTest {

  /**
   * Die mitgelieferten Standardwerte als eigene Literale — bewusst <em>nicht</em> als Verweis auf
   * die Konstanten der Prüfklasse: Erst so schreibt der Test unabhängig fest, welche konkreten
   * Werte als unsicher gelten. Gegen dieselbe Konstante zu prüfen wäre tautologisch — wer einen
   * Wert stillschweigend umschriebe, bliebe grün.
   */
  private static final String STANDARD_ENDPUNKT = "http://localhost:9000";

  private static final String STANDARD_ZUGANGSSCHLUESSEL = "manban";

  /** Standardwert aus {@code application.yml} und dem Kompaktkonstruktor der Properties. */
  private static final String STANDARD_GEHEIMNIS_ANWENDUNG = "manban-minio";

  /** Standardwert der {@code :-}-Vorgabe in {@code docker-compose.yml}. */
  private static final String STANDARD_GEHEIMNIS_COMPOSE = "manban-objektspeicher";

  /** Standardwert aus {@code .env.example}. */
  private static final String STANDARD_GEHEIMNIS_ENV_VORLAGE = "change-me-objektspeicher";

  /** Bewusst gesetzte eigene Werte — nie Teil einer Ausgabe (AK 3). */
  private static final String EIGENER_ENDPUNKT = "https://speicher.example.org:8333";

  private static final String EIGENER_ZUGANGSSCHLUESSEL = "zugang-4f1c9a77d2b8e0356ac41d9f8b27e603";

  private static final String EIGENES_GEHEIMNIS = "geheim-5cc1a49d7e2b8f6031ac5d9e7b204f18";

  /**
   * Fängt die Log-Ausgabe der Prüf-Bean. Der Logger ist global — deshalb im {@code @BeforeEach}
   * anmelden und im {@code @AfterEach} wieder abmelden, sonst sammelte der Appender über
   * Testklassen hinweg weiter.
   */
  private final ListAppender<ILoggingEvent> logWatcher = new ListAppender<>();

  @BeforeEach
  void watchStartupCheckLog() {
    logWatcher.start();
    ((Logger) LoggerFactory.getLogger(ObjectStorageStartupCheck.class)).addAppender(logWatcher);
  }

  @AfterEach
  void unwatchStartupCheckLog() {
    ((Logger) LoggerFactory.getLogger(ObjectStorageStartupCheck.class)).detachAppender(logWatcher);
  }

  /**
   * Je Feld jede einzelne Bedingung: fehlend, leer, nur Leerraum und jeder mitgelieferte
   * Standardwert. Erwartet wird jeweils der Abbruch samt Nennung von Umgebungsvariable und
   * Property-Name des betroffenen Feldes.
   */
  static Stream<Arguments> unsichereFelder() {
    return Stream.of(
        Arguments.of(
            "Endpunkt fehlt",
            props(null, EIGENER_ZUGANGSSCHLUESSEL, EIGENES_GEHEIMNIS),
            "MANBAN_STORAGE_ENDPOINT",
            "manban.storage.endpoint"),
        Arguments.of(
            "Endpunkt leer",
            props("", EIGENER_ZUGANGSSCHLUESSEL, EIGENES_GEHEIMNIS),
            "MANBAN_STORAGE_ENDPOINT",
            "manban.storage.endpoint"),
        Arguments.of(
            "Endpunkt nur Leerraum",
            props("   ", EIGENER_ZUGANGSSCHLUESSEL, EIGENES_GEHEIMNIS),
            "MANBAN_STORAGE_ENDPOINT",
            "manban.storage.endpoint"),
        Arguments.of(
            "Endpunkt auf Standardwert",
            props(STANDARD_ENDPUNKT, EIGENER_ZUGANGSSCHLUESSEL, EIGENES_GEHEIMNIS),
            "MANBAN_STORAGE_ENDPOINT",
            "manban.storage.endpoint"),
        Arguments.of(
            "Zugangsschlüssel fehlt",
            props(EIGENER_ENDPUNKT, null, EIGENES_GEHEIMNIS),
            "MANBAN_STORAGE_ACCESS_KEY",
            "manban.storage.access-key"),
        Arguments.of(
            "Zugangsschlüssel leer",
            props(EIGENER_ENDPUNKT, "", EIGENES_GEHEIMNIS),
            "MANBAN_STORAGE_ACCESS_KEY",
            "manban.storage.access-key"),
        Arguments.of(
            "Zugangsschlüssel nur Leerraum",
            props(EIGENER_ENDPUNKT, "  ", EIGENES_GEHEIMNIS),
            "MANBAN_STORAGE_ACCESS_KEY",
            "manban.storage.access-key"),
        Arguments.of(
            "Zugangsschlüssel auf Standardwert",
            props(EIGENER_ENDPUNKT, STANDARD_ZUGANGSSCHLUESSEL, EIGENES_GEHEIMNIS),
            "MANBAN_STORAGE_ACCESS_KEY",
            "manban.storage.access-key"),
        Arguments.of(
            "Geheimnis fehlt",
            props(EIGENER_ENDPUNKT, EIGENER_ZUGANGSSCHLUESSEL, null),
            "MANBAN_STORAGE_SECRET_KEY",
            "manban.storage.secret-key"),
        Arguments.of(
            "Geheimnis leer",
            props(EIGENER_ENDPUNKT, EIGENER_ZUGANGSSCHLUESSEL, ""),
            "MANBAN_STORAGE_SECRET_KEY",
            "manban.storage.secret-key"),
        Arguments.of(
            "Geheimnis nur Leerraum",
            props(EIGENER_ENDPUNKT, EIGENER_ZUGANGSSCHLUESSEL, " "),
            "MANBAN_STORAGE_SECRET_KEY",
            "manban.storage.secret-key"),
        Arguments.of(
            "Geheimnis auf Standardwert der Anwendung",
            props(EIGENER_ENDPUNKT, EIGENER_ZUGANGSSCHLUESSEL, STANDARD_GEHEIMNIS_ANWENDUNG),
            "MANBAN_STORAGE_SECRET_KEY",
            "manban.storage.secret-key"),
        Arguments.of(
            "Geheimnis auf Standardwert der Composition",
            props(EIGENER_ENDPUNKT, EIGENER_ZUGANGSSCHLUESSEL, STANDARD_GEHEIMNIS_COMPOSE),
            "MANBAN_STORAGE_SECRET_KEY",
            "manban.storage.secret-key"),
        Arguments.of(
            "Geheimnis auf Standardwert der .env-Vorlage",
            props(EIGENER_ENDPUNKT, EIGENER_ZUGANGSSCHLUESSEL, STANDARD_GEHEIMNIS_ENV_VORLAGE),
            "MANBAN_STORAGE_SECRET_KEY",
            "manban.storage.secret-key"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("unsichereFelder")
  void unsichererZugang_ohneEntwicklungsbetrieb_verweigertDenStartUndMeldetError(
      String fall,
      ObjectStorageProperties properties,
      String umgebungsvariable,
      String propertyName) {
    // When / Then
    assertThatThrownBy(() -> new ObjectStorageStartupCheck(properties, false))
        .as(fall)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(umgebungsvariable)
        .hasMessageContaining(propertyName)
        .hasMessageContaining("manban.dev-mode=true");

    assertThat(meldungen(Level.ERROR)).hasSize(1);
    assertThat(meldungen(Level.ERROR).getFirst())
        .contains(umgebungsvariable)
        .contains(propertyName)
        .contains("manban.dev-mode=true");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("unsichereFelder")
  void unsichererZugang_imEntwicklungsbetrieb_laesstDenStartZuUndWarnt(
      String fall,
      ObjectStorageProperties properties,
      String umgebungsvariable,
      String propertyName) {
    // When
    new ObjectStorageStartupCheck(properties, true);

    // Then
    assertThat(meldungen(Level.WARN)).as(fall).hasSize(1);
    assertThat(meldungen(Level.WARN).getFirst()).contains(umgebungsvariable).contains(propertyName);
    assertThat(meldungen(Level.ERROR)).isEmpty();
  }

  /**
   * Alle drei Felder zugleich unsicher: Der Betreiber soll den vollständigen Befund in einer
   * Meldung vorfinden und nicht dreimal starten müssen, bis er alle Stellen kennt.
   */
  @Test
  void alleFelderUnsicher_nenntAlleDreiVariablenInEinerMeldung() {
    // Given
    ObjectStorageProperties properties =
        props(STANDARD_ENDPUNKT, STANDARD_ZUGANGSSCHLUESSEL, STANDARD_GEHEIMNIS_ANWENDUNG);

    // When / Then
    assertThatThrownBy(() -> new ObjectStorageStartupCheck(properties, false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MANBAN_STORAGE_ENDPOINT")
        .hasMessageContaining("MANBAN_STORAGE_ACCESS_KEY")
        .hasMessageContaining("MANBAN_STORAGE_SECRET_KEY");

    assertThat(meldungen(Level.ERROR)).hasSize(1);
  }

  @Test
  void eigenerZugang_ohneEntwicklungsbetrieb_schweigt() {
    // Given
    ObjectStorageProperties properties =
        props(EIGENER_ENDPUNKT, EIGENER_ZUGANGSSCHLUESSEL, EIGENES_GEHEIMNIS);

    // When
    new ObjectStorageStartupCheck(properties, false);

    // Then
    assertThat(logWatcher.list).isEmpty();
  }

  @Test
  void eigenerZugang_imEntwicklungsbetrieb_schweigt() {
    // Given
    ObjectStorageProperties properties =
        props(EIGENER_ENDPUNKT, EIGENER_ZUGANGSSCHLUESSEL, EIGENES_GEHEIMNIS);

    // When
    new ObjectStorageStartupCheck(properties, true);

    // Then
    assertThat(logWatcher.list).isEmpty();
  }

  /**
   * AK 3: Kein Schlüsselwert verlässt die Klasse — weder ein eigener noch ein mitgelieferter, auch
   * nicht gekürzt oder als Prüfsumme. Geprüft an Ausnahmemeldung, ERROR- und WARN-Zeile. Der Wert
   * {@code manban} des Zugangsschlüssel-Standards bleibt außen vor: Er ist als Namensbestandteil in
   * jeder Variablen- und Property-Nennung enthalten, eine Prüfung darauf könnte nie halten. Er ist
   * öffentlich bekannt und trägt keinen Geheimnisanteil; die drei Geheimnis-Standardwerte tun es.
   */
  @Test
  void keineAusgabeNenntEinenSchluesselwert() {
    // Given — Endpunkt auf dem Standardwert, Zugangsdaten eigen: So schlägt die Prüfung an, und
    // zugleich liegen eigene Werte vor, die nicht in der Ausgabe stehen dürfen.
    ObjectStorageProperties standard =
        props(STANDARD_ENDPUNKT, EIGENER_ZUGANGSSCHLUESSEL, EIGENES_GEHEIMNIS);
    ObjectStorageProperties durchgehendStandard =
        props(STANDARD_ENDPUNKT, STANDARD_ZUGANGSSCHLUESSEL, STANDARD_GEHEIMNIS_ANWENDUNG);

    // When
    String ausnahmeMeldung =
        catchThrowable(() -> new ObjectStorageStartupCheck(standard, false)).getMessage();
    new ObjectStorageStartupCheck(durchgehendStandard, true);

    // Then
    assertThat(ausnahmeMeldung)
        .doesNotContain(EIGENER_ZUGANGSSCHLUESSEL)
        .doesNotContain(EIGENES_GEHEIMNIS);
    assertThat(meldungen(Level.ERROR)).hasSize(1);
    assertThat(meldungen(Level.WARN)).hasSize(1);
    assertThat(logWatcher.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
        .allSatisfy(
            meldung ->
                assertThat(meldung)
                    .doesNotContain(EIGENER_ZUGANGSSCHLUESSEL)
                    .doesNotContain(EIGENES_GEHEIMNIS)
                    .doesNotContain(STANDARD_GEHEIMNIS_ANWENDUNG)
                    .doesNotContain(STANDARD_GEHEIMNIS_COMPOSE)
                    .doesNotContain(STANDARD_GEHEIMNIS_ENV_VORLAGE));
  }

  /**
   * Mock statt echtem Record: Der Kompaktkonstruktor von {@link ObjectStorageProperties} überführt
   * {@code null} und Leerraum in den mitgelieferten Standardwert, ein echter Record kann diese
   * Werte also gar nicht tragen. Der Rückfall bleibt bewusst bestehen (Issue #1227, Aufgabe 3) —
   * geprüft wird, dass die Startprüfung nicht von ihm abhängt.
   */
  private static ObjectStorageProperties props(
      String endpoint, String accessKey, String secretKey) {
    ObjectStorageProperties properties = mock(ObjectStorageProperties.class);
    when(properties.endpoint()).thenReturn(endpoint);
    when(properties.accessKey()).thenReturn(accessKey);
    when(properties.secretKey()).thenReturn(secretKey);
    return properties;
  }

  /** Vergleich über {@code levelInt}: {@link Level} überschreibt {@code equals} nicht. */
  private List<String> meldungen(Level level) {
    return logWatcher.list.stream()
        .filter(event -> event.getLevel().levelInt == level.levelInt)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }
}
