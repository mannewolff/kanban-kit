package org.mwolff.manban.auth.infrastructure.security;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AuthProperties;
import org.slf4j.LoggerFactory;

/**
 * Die Startprüfung des Sitzungsschlüssels (Issue #890, fachlich #839).
 *
 * <p>Geprüft werden drei beobachtbare Ergebnisse: Ausnahme (unsicher im Produktivbetrieb), Warnung
 * (unsicher im ausdrücklich eingeschalteten Entwicklungs-/Testbetrieb) und Schweigen (sicher). Die
 * Wahl des Log-Levels ist dabei beabsichtigtes Verhalten und kein Beiwerk: ERROR begleitet den
 * Startabbruch, WARN ist der dauerhafte Hinweis an einen Betreiber, der bewusst unsicher fährt.
 */
class SessionSecretStartupCheckTest {

  /** Ein bewusst gesetzter eigener Schlüssel — nie Teil einer Ausgabe (AK 5). */
  private static final String EIGENER_SCHLUESSEL =
      "4f1c9a77d2b8e0356ac41d9f8b27e6035cc1a49d7e2b8f6031ac5d9e7b204f18";

  /**
   * Fängt die Log-Ausgabe der Prüf-Bean. Der Logger ist global — deshalb im {@code @BeforeEach}
   * anmelden und im {@code @AfterEach} wieder abmelden, sonst sammelte der Appender über
   * Testklassen hinweg weiter.
   */
  private final ListAppender<ILoggingEvent> logWatcher = new ListAppender<>();

  @BeforeEach
  void watchStartupCheckLog() {
    logWatcher.start();
    ((Logger) LoggerFactory.getLogger(SessionSecretStartupCheck.class)).addAppender(logWatcher);
  }

  @AfterEach
  void unwatchStartupCheckLog() {
    ((Logger) LoggerFactory.getLogger(SessionSecretStartupCheck.class)).detachAppender(logWatcher);
  }

  @Test
  void standardschluessel_ohneEntwicklungsbetrieb_verweigertDenStartUndMeldetError() {
    // Given
    AuthProperties props = mitSchluessel(AuthProperties.INSECURE_DEFAULT_SESSION_SECRET);

    // When / Then
    assertThatThrownBy(() -> new SessionSecretStartupCheck(props, false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("manban.auth.session-secret")
        .hasMessageContaining("MANBAN_SESSION_SECRET")
        .hasMessageContaining("openssl rand -hex 32")
        .hasMessageContaining("manban.dev-mode=true");

    assertThat(meldungen(Level.ERROR)).hasSize(1);
    assertThat(meldungen(Level.ERROR).getFirst())
        .contains("manban.auth.session-secret")
        .contains("MANBAN_SESSION_SECRET")
        .contains("openssl rand -hex 32")
        .contains("manban.dev-mode=true");
  }

  @Test
  void leererSchluessel_ohneEntwicklungsbetrieb_verweigertDenStart() {
    // Given — der Kompaktkonstruktor von AuthProperties könnte einen leeren Wert gar nicht
    // durchlassen; die Prüfung hängt bewusst nicht an diesem Rückfall (E4).
    AuthProperties props = mitRohemSchluessel("");

    // When / Then
    assertThatThrownBy(() -> new SessionSecretStartupCheck(props, false))
        .isInstanceOf(IllegalStateException.class);
    assertThat(meldungen(Level.ERROR)).hasSize(1);
  }

  @Test
  void nurLeerraumAlsSchluessel_ohneEntwicklungsbetrieb_verweigertDenStart() {
    // Given
    AuthProperties props = mitRohemSchluessel("   ");

    // When / Then
    assertThatThrownBy(() -> new SessionSecretStartupCheck(props, false))
        .isInstanceOf(IllegalStateException.class);
    assertThat(meldungen(Level.ERROR)).hasSize(1);
  }

  @Test
  void fehlenderSchluessel_ohneEntwicklungsbetrieb_verweigertDenStart() {
    // Given
    AuthProperties props = mitRohemSchluessel(null);

    // When / Then
    assertThatThrownBy(() -> new SessionSecretStartupCheck(props, false))
        .isInstanceOf(IllegalStateException.class);
    assertThat(meldungen(Level.ERROR)).hasSize(1);
  }

  @Test
  void standardschluessel_imEntwicklungsbetrieb_laesstDenStartZuUndWarnt() {
    // Given
    AuthProperties props = mitSchluessel(AuthProperties.INSECURE_DEFAULT_SESSION_SECRET);

    // When
    new SessionSecretStartupCheck(props, true);

    // Then
    assertThat(meldungen(Level.WARN)).hasSize(1);
    assertThat(meldungen(Level.ERROR)).isEmpty();
  }

  @Test
  void eigenerSchluessel_imEntwicklungsbetrieb_schweigt() {
    // Given
    AuthProperties props = mitSchluessel(EIGENER_SCHLUESSEL);

    // When
    new SessionSecretStartupCheck(props, true);

    // Then
    assertThat(logWatcher.list).isEmpty();
  }

  @Test
  void eigenerSchluessel_ohneEntwicklungsbetrieb_schweigt() {
    // Given
    AuthProperties props = mitSchluessel(EIGENER_SCHLUESSEL);

    // When
    new SessionSecretStartupCheck(props, false);

    // Then
    assertThat(logWatcher.list).isEmpty();
  }

  /**
   * AK 5: Kein Schlüsselwert verlässt die Klasse. Für den Standardwert wird das an Ausnahme, ERROR-
   * und WARN-Zeile geprüft; für einen eigenen Wert ist es dadurch gedeckt, dass die Klasse bei
   * sicherem Schlüssel überhaupt nichts ausgibt (siehe die beiden vorangehenden Tests) — hier
   * zusätzlich ausdrücklich über den gesamten Appender-Inhalt festgehalten.
   */
  @Test
  void keineAusgabeNenntEinenSchluesselwert() {
    // Given
    AuthProperties standard = mitSchluessel(AuthProperties.INSECURE_DEFAULT_SESSION_SECRET);

    // When
    String ausnahmeMeldung =
        catchThrowable(() -> new SessionSecretStartupCheck(standard, false)).getMessage();
    new SessionSecretStartupCheck(standard, true);
    new SessionSecretStartupCheck(mitSchluessel(EIGENER_SCHLUESSEL), false);

    // Then
    assertThat(ausnahmeMeldung)
        .doesNotContain(AuthProperties.INSECURE_DEFAULT_SESSION_SECRET)
        .doesNotContain(EIGENER_SCHLUESSEL);
    assertThat(meldungen(Level.ERROR)).hasSize(1);
    assertThat(meldungen(Level.WARN)).hasSize(1);
    assertThat(logWatcher.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
        .allSatisfy(
            meldung ->
                assertThat(meldung)
                    .doesNotContain(AuthProperties.INSECURE_DEFAULT_SESSION_SECRET)
                    .doesNotContain(EIGENER_SCHLUESSEL));
  }

  /** Echter Record — trägt jeden Wert, den der Kompaktkonstruktor durchlässt. */
  private static AuthProperties mitSchluessel(String sessionSecret) {
    return new AuthProperties(null, null, sessionSecret, null, null, null);
  }

  /**
   * Mock statt Record für {@code null} und Leerraum: Der Kompaktkonstruktor überführt beides in den
   * Standardwert (AuthProperties, Zeilen 36-38), ein echter Record kann diese Werte also gar nicht
   * tragen. Der Rückfall bleibt bewusst bestehen — geprüft wird, dass die Startprüfung nicht von
   * ihm abhängt (E4).
   */
  private static AuthProperties mitRohemSchluessel(String sessionSecret) {
    AuthProperties props = mock(AuthProperties.class);
    when(props.sessionSecret()).thenReturn(sessionSecret);
    return props;
  }

  /** Vergleich über {@code levelInt}: {@link Level} überschreibt {@code equals} nicht. */
  private List<String> meldungen(Level level) {
    return logWatcher.list.stream()
        .filter(event -> event.getLevel().levelInt == level.levelInt)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }
}
