package org.mwolff.manban.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AuthProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Die Startprüfung wirkt auf den Anwendungskontext, nicht nur auf den Konstruktoraufruf (Issue
 * #890, E14): Mit unsicherem Schlüssel und ohne ausdrücklichen Entwicklungsbetrieb kommt der
 * Kontext gar nicht erst hoch — der Weg, auf dem die Anwendung den Start verweigert.
 *
 * <p>{@link PropertyPlaceholderAutoConfiguration} ist bewusst mitgeladen: Ohne einen
 * Placeholder-Configurer im Kontext bliebe {@code @Value("${manban.dev-mode:false}")} unaufgelöst
 * und der Test prüfte die Property gar nicht.
 */
class SessionSecretStartupCheckContextTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
          .withBean(
              AuthProperties.class,
              () ->
                  new AuthProperties(
                      null, null, AuthProperties.INSECURE_DEFAULT_SESSION_SECRET, null, null, null))
          .withUserConfiguration(SessionSecretStartupCheck.class);

  @Test
  void standardschluesselOhneEntwicklungsbetrieb_laesstDenKontextScheitern() {
    runner.run(
        context ->
            assertThat(context)
                .hasFailed()
                .getFailure()
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("manban.auth.session-secret")
                .hasMessageContaining("MANBAN_SESSION_SECRET")
                .hasMessageContaining("openssl rand -hex 32")
                .hasMessageContaining("manban.dev-mode=true"));
  }

  @Test
  void standardschluesselMitEntwicklungsbetrieb_laesstDenKontextHochkommen() {
    runner
        .withPropertyValues("manban.dev-mode=true")
        .run(
            context ->
                assertThat(context).hasNotFailed().hasSingleBean(SessionSecretStartupCheck.class));
  }
}
