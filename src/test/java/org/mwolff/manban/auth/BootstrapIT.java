package org.mwolff.manban.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-End-Test des Admin-Bootstraps (Token konfiguriert, frische Instanz ohne Admin): Der erste
 * Nutzer meldet sich per Code an und hebt sich mit dem Bootstrap-Token zum Plattform-Admin
 * (fachliche Quelle #1781, AK 10; Issue #1813).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = "manban.bootstrap.admin-token=it-boot-token")
class BootstrapIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private TestAnmeldung anmeldung;

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
    anmeldung.konto("boot-first@example.com", PlatformRole.USER);
    Cookie user = anmeldung.perCode(mvc, "boot-first@example.com");

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
