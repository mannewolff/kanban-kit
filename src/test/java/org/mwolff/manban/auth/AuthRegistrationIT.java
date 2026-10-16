package org.mwolff.manban.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-End-Test der Registrierung gegen echtes Postgres: Ein Konto braucht nur E-Mail und
 * Anzeigename, im selben Zug wird ein Anmeldecode angefordert, eine Bestätigungsmail geht nicht
 * mehr hinaus (Plan #1801, E2, E3). Die Mails stehen als Outbox-Einträge in der Datenbank.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AuthRegistrationIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private AppUserRepository users;

  @Autowired private JdbcTemplate jdbc;

  private static String registerBody(String email) {
    return """
                {"email":"%s","displayName":"Alice"}
                """
        .formatted(email);
  }

  private List<String> mailTypes() {
    return jdbc.queryForList("SELECT event_type FROM outbox_entry ORDER BY id", String.class);
  }

  @Test
  void registerWithoutPasswordCreatesUnverifiedUserAndRequestsOneLoginCode() throws Exception {
    String email = "happy@example.com";

    mvc.perform(
            post("/api/auth/register").contentType("application/json").content(registerBody(email)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.emailVerified").value(false));

    assertThat(users.findByEmail(email))
        .get()
        .satisfies(u -> assertThat(u.emailVerified()).isFalse());
    assertThat(mailTypes()).containsExactly("mail.login-code");
  }

  @Test
  void passwordFromAnOldClientIsIgnored() throws Exception {
    // Das Feld gibt es nicht mehr; ein alter Client mit (auch zu kurzem) Passwort registriert
    // trotzdem, und das Passwort landet nirgends.
    mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content(
                    "{\"email\":\"alt@example.com\",\"password\":\"x\","
                        + "\"displayName\":\"Alt\"}"))
        .andExpect(status().isCreated());

    assertThat(mailTypes()).containsExactly("mail.login-code");
  }

  @Test
  void duplicateEmailReturns409() throws Exception {
    String email = "dupe@example.com";
    mvc.perform(
            post("/api/auth/register").contentType("application/json").content(registerBody(email)))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content(registerBody("DUPE@example.com")))
        .andExpect(status().isConflict());

    // Die abgewiesene Registrierung fordert keinen zweiten Code an.
    assertThat(mailTypes()).containsExactly("mail.login-code");
  }

  @Test
  void registrationOverTheAddressLimitIsRejectedAndLeavesNoAccount() throws Exception {
    String email = "limit@example.com";
    for (int anforderung = 1; anforderung <= 5; anforderung++) {
      mvc.perform(
              post("/api/auth/code/request")
                  .contentType("application/json")
                  .content("{\"email\":\"%s\"}".formatted(email)))
          .andExpect(status().isAccepted());
    }

    mvc.perform(
            post("/api/auth/register").contentType("application/json").content(registerBody(email)))
        .andExpect(status().isTooManyRequests());

    assertThat(users.findByEmail(email)).isEmpty();
  }

  @Test
  void invalidRequestBodyReturns400() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content("{\"email\":\"not-an-email\",\"displayName\":\"\"}"))
        .andExpect(status().isBadRequest());
  }
}
