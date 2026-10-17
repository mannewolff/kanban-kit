package org.mwolff.manban.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.ratelimit.application.AttemptStore;
import org.mwolff.manban.ratelimit.application.RateLimitedOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Die alten Variablen der Bremse wirken als Rückfall für die Code-Anmeldung (Issue #1807, Plan
 * #1801 A7).
 *
 * <p>Ein Betreiber, der die Anmeldung über {@code MANBAN_RATELIMIT_LOGIN_MAX_ATTEMPTS} enger
 * gestellt hat, verlöre mit der Umstellung auf den Einmalcode sonst still seine Einstellung: Die
 * Code-Eingabe liefe mit der Vorgabe von zehn. Ebenso erbt die Code-Anforderung den Wert der
 * Reset-Anforderung. Gesetzt wird die Variable als Eigenschaft des Testkontexts — die Platzhalter
 * in {@code application.yml} lösen Umgebung und Eigenschaften gleich auf, und eine echte
 * Umgebungsvariable ließe sich im laufenden Prozess nicht setzen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "manban.ratelimit.enabled=true",
      "MANBAN_RATELIMIT_LOGIN_MAX_ATTEMPTS=2",
      "MANBAN_RATELIMIT_FORGOT_MAX_ATTEMPTS=2"
    })
class RateLimitAltvariablenIT extends AbstractIntegrationTest {

  /** Die Herkunft eines Aufrufs ohne Weiterleitungsheader — die Vorgabe von MockMvc. */
  private static final String ORIGIN = new MockHttpServletRequest().getRemoteAddr();

  private static final int OLD_MAX_ATTEMPTS = 2;

  @Autowired private MockMvc mvc;

  @Autowired private AttemptStore attempts;

  /** Der Zählstand hängt am gecachten Kontext und überlebt den {@code TRUNCATE} der Basisklasse. */
  @BeforeEach
  void resetTheBrake() {
    for (RateLimitedOperation operation : RateLimitedOperation.values()) {
      attempts.remove(new AttemptStore.Key(ORIGIN, operation));
    }
  }

  private int verifyWrongCode() throws Exception {
    return mvc.perform(
            post("/api/auth/code/verify")
                .contentType("application/json")
                .content("{\"email\":\"alt@example.com\",\"code\":\"000000\"}"))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private int requestCode(String email) throws Exception {
    return mvc.perform(
            post("/api/auth/code/request")
                .contentType("application/json")
                .content("{\"email\":\"%s\"}".formatted(email)))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  @Test
  void dieCodeEingabeGreiftBeimWertDerAltenAnmeldevariable() throws Exception {
    // Given — zwei statt zehn Fehlversuche sind offen.
    for (int versuch = 1; versuch <= OLD_MAX_ATTEMPTS; versuch++) {
      assertThat(verifyWrongCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    // When / Then — der dritte ist abgewiesen, obwohl die Vorgabe zehn wäre.
    assertThat(verifyWrongCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
  }

  @Test
  void dieCodeAnforderungGreiftBeimWertDerAltenResetVariable() throws Exception {
    // Given — zwei Anforderungen sind offen, je eine eigene Adresse.
    for (int aufruf = 1; aufruf <= OLD_MAX_ATTEMPTS; aufruf++) {
      assertThat(requestCode("alt" + aufruf + "@example.com"))
          .isEqualTo(HttpStatus.ACCEPTED.value());
    }

    // When / Then
    assertThat(requestCode("alt-zu-viel@example.com"))
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
  }
}
