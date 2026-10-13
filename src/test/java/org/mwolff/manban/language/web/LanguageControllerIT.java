package org.mwolff.manban.language.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Lesen ohne Anmeldung und Ändern durch den Plattform-Admin (Issue #1639, Plan #1638 A2/E3): {@code
 * GET /api/language} ist öffentlich, {@code PUT /api/admin/language} verlangt die Sitzung eines
 * Plattform-Admins.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class LanguageControllerIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final String LESE_PFAD = "/api/language";
  private static final String ADMIN_PFAD = "/api/admin/language";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  @Test
  void lesenOhneAnmeldung_liefertDeutschAufLeererDatenbank() throws Exception {
    mvc.perform(get(LESE_PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.language").value("de"));
  }

  @Test
  void setzenOhneSitzung_wirdAbgewiesen() throws Exception {
    mvc.perform(setzen("en")).andExpect(status().isUnauthorized());
  }

  @Test
  void setzenAlsNichtAdmin_gibt403() throws Exception {
    Cookie user = session("sprache-user@example.com", PlatformRole.USER);

    mvc.perform(setzen("en").cookie(user)).andExpect(status().isForbidden());
    mvc.perform(get(LESE_PFAD)).andExpect(jsonPath("$.language").value("de"));
  }

  @Test
  void setzenMitZugriffsToken_wirdAbgewiesen() throws Exception {
    // Selbst das ungebundene Token eines Plattform-Admins nicht: /api/admin/** verlangt die
    // Sitzung.
    Cookie admin = session("sprache-token-admin@example.com", PlatformRole.ADMIN);
    String token = unboundToken(admin);

    mvc.perform(setzen("en").header("X-Kanban-Token", token)).andExpect(status().isForbidden());
    mvc.perform(get(LESE_PFAD)).andExpect(jsonPath("$.language").value("de"));
  }

  @Test
  void setzenAlsPlattformAdmin_wirktBeimNaechstenLesen() throws Exception {
    Cookie admin = session("sprache-admin@example.com", PlatformRole.ADMIN);

    mvc.perform(setzen("en").cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.language").value("en"));
    mvc.perform(get(LESE_PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.language").value("en"));

    mvc.perform(setzen("de").cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.language").value("de"));
    mvc.perform(get(LESE_PFAD)).andExpect(jsonPath("$.language").value("de"));
  }

  @Test
  void unbekannterWert_gibt400() throws Exception {
    Cookie admin = session("sprache-fr-admin@example.com", PlatformRole.ADMIN);

    mvc.perform(setzen("fr").cookie(admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.language").exists());
  }

  @Test
  void fehlenderWert_gibt400() throws Exception {
    Cookie admin = session("sprache-leer-admin@example.com", PlatformRole.ADMIN);

    mvc.perform(put(ADMIN_PFAD).cookie(admin).contentType("application/json").content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.language").exists());
  }

  private static MockHttpServletRequestBuilder setzen(String sprache) {
    return put(ADMIN_PFAD)
        .contentType("application/json")
        .content("{\"language\":\"%s\"}".formatted(sprache));
  }

  private String unboundToken(Cookie session) throws Exception {
    String body =
        mvc.perform(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"cli\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("plaintext").asText();
  }

  private Cookie session(String email, PlatformRole role) throws Exception {
    users.save(new AppUser(null, email, passwordEncoder.encode(PASSWORD), "P", true, role));
    return mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("manban_session");
  }
}
