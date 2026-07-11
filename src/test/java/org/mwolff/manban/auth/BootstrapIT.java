package org.mwolff.manban.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-End-Test des Admin-Bootstraps (Token konfiguriert, frische Instanz ohne Admin). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = "manban.bootstrap.admin-token=it-boot-token")
class BootstrapIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;

  private Cookie login(String email) throws Exception {
    if (users.findByEmail(email).isEmpty()) {
      users.save(
          new AppUser(
              null, email, passwordEncoder.encode(PASSWORD), "Person", true, PlatformRole.USER));
    }
    return mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("manban_session");
  }

  @Test
  void unauthenticatedBootstrapRejected() throws Exception {
    mvc.perform(
            post("/api/admin/bootstrap")
                .contentType("application/json")
                .content("{\"token\":\"it-boot-token\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void loggedInUserBecomesAdminWithCorrectToken() throws Exception {
    Cookie user = login("boot-first@example.com");

    mvc.perform(
            post("/api/admin/bootstrap")
                .cookie(user)
                .contentType("application/json")
                .content("{\"token\":\"it-boot-token\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.platformRole").value("ADMIN"));

    // Danach ist der Nutzer Admin.
    org.assertj.core.api.Assertions.assertThat(
            users.findByEmail("boot-first@example.com").orElseThrow().platformRole())
        .isEqualTo(PlatformRole.ADMIN);
  }
}
