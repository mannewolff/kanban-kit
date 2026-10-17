package org.mwolff.manban.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Security-Slice: volle Filterkette gegen echtes Postgres. Deckt die Anmeldung per Code (Cookie),
 * /api/me, Default-Deny ohne Cookie (401), den Wegfall der Passwort-Anmeldung und das Abmelden ab:
 * Es löscht das Cookie dieses Geräts, danach antwortet /api/me mit 401 (Plan #1801, E4; Issue
 * #1813). Falsche, abgelaufene und erratene Codes prüft {@link AnmeldungPerCodeSzenarienIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AuthSessionIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private TestAnmeldung anmeldung;

  @Autowired private JdbcTemplate jdbc;

  @Test
  void codeLoginSetsCookieAndMeReturnsUser() throws Exception {
    anmeldung.konto("verified@example.com", PlatformRole.USER);

    Cookie session = anmeldung.perCode(mvc, "verified@example.com");

    assertThat(session.getValue()).isNotBlank();
    assertThat(session.isHttpOnly()).isTrue();
    mvc.perform(get("/api/me").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("verified@example.com"))
        .andExpect(jsonPath("$.platformRole").value("USER"))
        .andExpect(jsonPath("$.memberships").isArray());
  }

  /**
   * Kundenfall aus Issue #556: Nach der dokumentierten Erst-Admin-Einrichtung per SQL — verifiziert
   * und ADMIN, aber ohne {@code approved_at} — muss die Anmeldung gelingen. Der Nutzer ist selbst
   * der einzige Admin und sperrte sich sonst mit dem Freigabe-Gate aus.
   */
  @Test
  void pendingPlatformAdminCanLogIn() throws Exception {
    String email = "selfadmin@example.com";
    mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content(
                    """
                    {"email":"%s","displayName":"Self Admin"}
                    """
                        .formatted(email)))
        .andExpect(status().isCreated());

    // Exakt der dokumentierte Befehl: setzt Verifikation und Rolle, aber keine Freigabe.
    jdbc.update(
        "UPDATE app_user SET email_verified = true, platform_role = 'ADMIN' WHERE email = ?",
        email);

    MockHttpServletResponse eingabe = anmeldung.codeEinloesen(mvc, email);

    assertThat(eingabe.getStatus()).isEqualTo(HttpStatus.OK.value());
    assertThat(eingabe.getContentAsString()).contains("\"platformRole\":\"ADMIN\"");
  }

  @Test
  void meWithoutCookieReturns401() throws Exception {
    mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
  }

  /** Abmelden beendet die Sitzung auf diesem Gerät: Cookie gelöscht, danach 401 (AK 5, E4). */
  @Test
  void logoutClearsCookieAndEndsTheSessionOnThisDevice() throws Exception {
    anmeldung.konto("logout@example.com", PlatformRole.USER);
    Cookie session = anmeldung.perCode(mvc, "logout@example.com");
    mvc.perform(get("/api/me").cookie(session)).andExpect(status().isOk());

    MvcResult logout =
        mvc.perform(post("/api/auth/logout").cookie(session))
            .andExpect(status().isNoContent())
            .andReturn();

    Cookie cleared = logout.getResponse().getCookie("manban_session");
    assertThat(cleared).isNotNull();
    assertThat(cleared.getMaxAge()).isZero();
    // Der Browser übernimmt das gelöschte Cookie und schickt danach nur noch dieses mit.
    mvc.perform(get("/api/me").cookie(cleared)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
  }

  /**
   * Die Anmeldung mit Passwort ist entfallen (Plan #1801, A13; Issue #1816): Auch mit gültiger
   * Sitzung gibt es den Endpunkt nicht mehr. Ohne Sitzung antwortete schon der Default-Deny mit 401
   * und verdeckte, ob es ihn noch gibt.
   */
  @Test
  void passwordLoginNoLongerExists() throws Exception {
    anmeldung.konto("ohne-passwort@example.com", PlatformRole.USER);
    Cookie session = anmeldung.perCode(mvc, "ohne-passwort@example.com");

    int status =
        mvc.perform(
                post("/api/auth/{pfad}", "login")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"email\":\"ohne-passwort@example.com\",\"password\":\"x\"}"))
            .andReturn()
            .getResponse()
            .getStatus();

    assertThat(status).isIn(HttpStatus.NOT_FOUND.value(), HttpStatus.METHOD_NOT_ALLOWED.value());
  }

  @Test
  void tamperedCookieIsRejected() throws Exception {
    mvc.perform(get("/api/me").cookie(new Cookie("manban_session", "forged.token")))
        .andExpect(status().isUnauthorized());
  }

  /**
   * Regressionsschutz: die React-App unter / bleibt trotz aktiver Security ohne Login erreichbar.
   */
  @Test
  void reactAppServedAtRootWithoutAuth() throws Exception {
    mvc.perform(get("/")).andExpect(status().isOk());
  }
}
