package org.mwolff.manban.attachment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.attachment.application.ObjectStorageProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Die Startprüfung wirkt auf den Anwendungskontext, nicht nur auf den Konstruktoraufruf (Issue
 * #1227, AK 2): Ein Kontext mit den mitgelieferten Standardzugangsdaten und ohne ausdrücklichen
 * Entwicklungsbetrieb kommt gar nicht erst hoch — der Weg, auf dem die Anwendung den Start
 * verweigert, statt Anhänge in einen offenen Speicher zu schreiben.
 *
 * <p>Die Properties entstehen hier als <em>echter</em> Record aus lauter {@code null}: Genau so
 * füllt sein Kompaktkonstruktor alle drei Werte mit den Standardwerten aus dem öffentlichen
 * Repository — der Zustand, den ein Betreiber ohne eigene {@code .env} vorfindet.
 *
 * <p>{@link PropertyPlaceholderAutoConfiguration} ist bewusst mitgeladen: Ohne einen
 * Placeholder-Configurer im Kontext bliebe {@code @Value("${manban.dev-mode:false}")} unaufgelöst
 * und der Test prüfte die Property gar nicht.
 */
class ObjectStorageStartupCheckContextTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
          .withBean(
              ObjectStorageProperties.class,
              () -> new ObjectStorageProperties(null, null, null, null, null))
          .withUserConfiguration(ObjectStorageStartupCheck.class);

  @Test
  void standardzugangsdatenOhneEntwicklungsbetrieb_laesstDenKontextScheitern() {
    runner.run(
        context ->
            assertThat(context)
                .hasFailed()
                .getFailure()
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MANBAN_STORAGE_ENDPOINT")
                .hasMessageContaining("MANBAN_STORAGE_ACCESS_KEY")
                .hasMessageContaining("MANBAN_STORAGE_SECRET_KEY")
                .hasMessageContaining("manban.dev-mode=true"));
  }

  @Test
  void standardzugangsdatenMitEntwicklungsbetrieb_laesstDenKontextHochkommen() {
    runner
        .withPropertyValues("manban.dev-mode=true")
        .run(
            context ->
                assertThat(context).hasNotFailed().hasSingleBean(ObjectStorageStartupCheck.class));
  }
}
