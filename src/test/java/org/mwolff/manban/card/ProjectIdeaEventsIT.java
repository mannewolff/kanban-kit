package org.mwolff.manban.card;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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

/**
 * IT der Ideen-Pool-SSE-Autorisierung (Mitglied abonniert, Fremde/unbekanntes Projekt abgelehnt).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ProjectIdeaEventsIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  @Test
  void memberCanSubscribe_othersAndUnknownProjectRejected() throws Exception {
    Cookie owner = session("idea-events-owner@example.com", PlatformRole.USER);
    Cookie admin = session("idea-events-admin@example.com", PlatformRole.ADMIN);
    Cookie stranger = session("idea-events-stranger@example.com", PlatformRole.USER);

    long projectId =
        json.readTree(
                mvc.perform(
                        post("/api/projects")
                            .cookie(admin)
                            .contentType("application/json")
                            .content(
                                "{\"name\":\"P\",\"ownerEmail\":"
                                    + "\"idea-events-owner@example.com\"}"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();

    // Mitglied: der Controller gibt einen SseEmitter zurück -> Spring startet die
    // async-Verarbeitung.
    mvc.perform(get("/api/projects/" + projectId + "/ideas/events").cookie(owner))
        .andExpect(request().asyncStarted());

    // Fremder (kein Mitglied): 404 (kein Existenz-Leak).
    mvc.perform(get("/api/projects/" + projectId + "/ideas/events").cookie(stranger))
        .andExpect(status().isNotFound());

    // Unbekanntes Projekt: 404.
    mvc.perform(get("/api/projects/999999/ideas/events").cookie(owner))
        .andExpect(status().isNotFound());
  }

  @Test
  void unauthenticatedIsRejected() throws Exception {
    mvc.perform(get("/api/projects/1/ideas/events")).andExpect(status().isUnauthorized());
  }

  private Cookie session(String email, PlatformRole role) throws Exception {
    if (users.findByEmail(email).isEmpty()) {
      users.save(new AppUser(null, email, passwordEncoder.encode(PASSWORD), "P", true, role));
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
}
