package org.mwolff.manban.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Die Startprüfung wirkt auf den Anwendungskontext, nicht nur auf den Konstruktoraufruf (Issue
 * #1266): Ein Kontext mit dem öffentlich bekannten Vorgabekennwort und ohne ausdrücklichen
 * Entwicklungsbetrieb kommt gar nicht erst hoch — der Weg, auf dem die Anwendung den Start
 * verweigert, statt ihre Daten unter einem bekannten Kennwort zu führen.
 *
 * <p>Ohne diesen Test bliebe die Auflösung der beiden {@code @Value}-Ausdrücke ungeprüft: Ein
 * Tippfehler im Property-Namen liefe im reinen Konstruktortest nicht auf, die Bean bekäme im
 * laufenden Betrieb aber nie das Kennwort zu sehen und schwiege zu jeder Instanz.
 *
 * <p>{@link PropertyPlaceholderAutoConfiguration} ist bewusst mitgeladen: Ohne einen
 * Placeholder-Configurer im Kontext bliebe {@code @Value("${manban.dev-mode:false}")} unaufgelöst
 * und der Test prüfte die Property gar nicht.
 */
class DatabasePasswordStartupCheckContextTest {

  /** Vorgabe aus {@code application.yml} und der {@code :-manban}-Vorgabe in Compose. */
  private static final String ABGELEHNT_VORGABE = "manban";

  /** Ein Kennwort, das ein Betreiber selbst gesetzt hat. */
  private static final String EIGENES_KENNWORT = "kennwort-9b3f61a0d47c28e5031ac5d9e7b204f18";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
          .withUserConfiguration(DatabasePasswordStartupCheck.class);

  @Test
  void vorgabeKennwortOhneEntwicklungsbetrieb_laesstDenKontextScheitern() {
    runner
        .withPropertyValues("spring.datasource.password=" + ABGELEHNT_VORGABE)
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("POSTGRES_PASSWORD")
                    .hasMessageContaining("MANBAN_DB_PASSWORD")
                    .hasMessageContaining("spring.datasource.password")
                    .hasMessageContaining("manban.dev-mode=true"));
  }

  /**
   * Fehlt die Property ganz, greift die leere Vorgabe des {@code @Value}-Ausdrucks — und der Start
   * scheitert mit derselben Meldung. Ohne diese Vorgabe bräche der Kontext an einem unauflösbaren
   * Platzhalter ab und nennte den fehlenden Wert gerade nicht.
   */
  @Test
  void fehlendesKennwortOhneEntwicklungsbetrieb_laesstDenKontextScheitern() {
    runner.run(
        context ->
            assertThat(context)
                .hasFailed()
                .getFailure()
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("POSTGRES_PASSWORD"));
  }

  @Test
  void vorgabeKennwortMitEntwicklungsbetrieb_laesstDenKontextHochkommen() {
    runner
        .withPropertyValues(
            "spring.datasource.password=" + ABGELEHNT_VORGABE, "manban.dev-mode=true")
        .run(
            context ->
                assertThat(context)
                    .hasNotFailed()
                    .hasSingleBean(DatabasePasswordStartupCheck.class));
  }

  @Test
  void eigenesKennwort_laesstDenKontextHochkommen() {
    runner
        .withPropertyValues("spring.datasource.password=" + EIGENES_KENNWORT)
        .run(
            context ->
                assertThat(context)
                    .hasNotFailed()
                    .hasSingleBean(DatabasePasswordStartupCheck.class));
  }
}
