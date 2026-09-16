package org.mwolff.manban.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Die Startprüfung ist im echten Anwendungskontext verdrahtet (Issue #890, E14).
 *
 * <p>Ohne diesen Test deckt nichts ab, dass die Bean beim Start überhaupt geladen wird: Der
 * Unit-Test ruft den Konstruktor selbst auf, der Kontext-Test registriert sie von Hand. Dass hier
 * ein Kontext hochkommt, ist zugleich der Beleg, dass der Test-Betrieb die Ausnahme aus AK 2 trägt
 * ({@code manban.dev-mode=true} in {@link AbstractIntegrationTest}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SessionSecretStartupCheckIT extends AbstractIntegrationTest {

  @Autowired private SessionSecretStartupCheck startupCheck;

  @Test
  void beanIstImAnwendungskontextVorhanden() {
    assertThat(startupCheck).isNotNull();
  }
}
