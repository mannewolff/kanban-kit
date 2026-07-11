package org.mwolff.manban.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Security-Slice: volle Filterkette gegen echtes Postgres. Deckt Login (Cookie), /api/me, falsche
 * Daten (401), unverifiziert (403), Default-Deny ohne Cookie (401) und Logout (Cookie-Löschung) ab.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class AuthSessionIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;

  @Autowired private AppUserRepository users;

  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void seedUsers() {
    if (users.findByEmail("verified@example.com").isEmpty()) {
      users.save(
          new AppUser(
              null,
              "verified@example.com",
              passwordEncoder.encode(PASSWORD),
              "Verified",
              true,
              PlatformRole.USER));
    }
    if (users.findByEmail("unverified@example.com").isEmpty()) {
      users.save(
          new AppUser(
              null,
              "unverified@example.com",
              passwordEncoder.encode(PASSWORD),
              "Unverified",
              false,
              PlatformRole.USER));
    }
  }

  private static String loginBody(String email, String password) {
    return """
                {"email":"%s","password":"%s"}
                """
        .formatted(email, password);
  }

  @Test
  void loginSetsCookieAndMeReturnsUser() throws Exception {
    MvcResult login =
        mvc.perform(
                post("/api/auth/login")
                    .contentType("application/json")
                    .content(loginBody("verified@example.com", PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("verified@example.com"))
            .andExpect(jsonPath("$.platformRole").value("USER"))
            .andExpect(jsonPath("$.memberships").isArray())
            .andReturn();

    Cookie session = login.getResponse().getCookie("manban_session");
    assertThat(session).isNotNull();
    assertThat(session.getValue()).isNotBlank();
    assertThat(session.isHttpOnly()).isTrue();

    mvc.perform(get("/api/me").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("verified@example.com"));
  }

  @Test
  void wrongPasswordReturns401() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content(loginBody("verified@example.com", "wrong-password")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unverifiedUserReturns403() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content(loginBody("unverified@example.com", PASSWORD)))
        .andExpect(status().isForbidden());
  }

  @Test
  void meWithoutCookieReturns401() throws Exception {
    mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void logoutClearsCookie() throws Exception {
    MvcResult logout =
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent()).andReturn();

    Cookie cleared = logout.getResponse().getCookie("manban_session");
    assertThat(cleared).isNotNull();
    assertThat(cleared.getMaxAge()).isZero();
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
