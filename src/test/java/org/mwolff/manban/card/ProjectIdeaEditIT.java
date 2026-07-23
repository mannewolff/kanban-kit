package org.mwolff.manban.card;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/**
 * End-to-End: board-lose Pool-Ideen (#405) sind voll editierbar — Titel/Beschreibung/Fälligkeit,
 * Zuständige, Kommentare und Aktivität, alles projekt-basiert geprüft. Nichtmitglieder bleiben
 * ausgesperrt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ProjectIdeaEditIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  @Test
  void boardlessIdea_isFullyEditableByMember_andBlockedForNonMember() throws Exception {
    Cookie owner = session("idea-edit-owner@example.com", PlatformRole.USER);
    Cookie admin = session("idea-edit-admin@example.com", PlatformRole.ADMIN);
    Cookie stranger = session("idea-edit-stranger@example.com", PlatformRole.USER);
    long ownerId = users.findByEmail("idea-edit-owner@example.com").orElseThrow().id();

    long projectId =
        json.readTree(
                mvc.perform(
                        post("/api/projects")
                            .cookie(admin)
                            .contentType("application/json")
                            .content(
                                "{\"name\":\"P\",\"ownerEmail\":\"idea-edit-owner@example.com\"}"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();

    // Board-lose Idee im Pool anlegen.
    long ideaId =
        json.readTree(
                mvc.perform(
                        post("/api/projects/" + projectId + "/ideas")
                            .cookie(owner)
                            .contentType("application/json")
                            .content("{\"title\":\"Rohidee\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.boardId").value(nullValue()))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();

    // Mitglied editiert Titel/Beschreibung/Fälligkeit — bleibt board-los.
    mvc.perform(
            patch("/api/cards/" + ideaId)
                .cookie(owner)
                .contentType("application/json")
                .content(
                    "{\"title\":\"Verfeinerte Idee\",\"description\":\"mehr Details\","
                        + "\"dueDate\":\"2026-09-01T00:00:00Z\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Verfeinerte Idee"))
        .andExpect(jsonPath("$.description").value("mehr Details"))
        .andExpect(jsonPath("$.boardId").value(nullValue()))
        .andExpect(jsonPath("$.ideaStored").value(true));

    // Mitglied setzt Zuständige (sich selbst — Projektmitglied).
    mvc.perform(
            put("/api/cards/" + ideaId + "/assignees")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"assignees\":[" + ownerId + "]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignees[0]").value((int) ownerId));

    // Mitglied kommentiert die Idee, liest die Liste und bearbeitet den Kommentar.
    long commentId =
        json.readTree(
                mvc.perform(
                        post("/api/cards/" + ideaId + "/comments")
                            .cookie(owner)
                            .contentType("application/json")
                            .content("{\"body\":\"Erster Gedanke\"}"))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();
    mvc.perform(get("/api/cards/" + ideaId + "/comments").cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].body").value("Erster Gedanke"));
    mvc.perform(
            patch("/api/comments/" + commentId)
                .cookie(owner)
                .contentType("application/json")
                .content("{\"body\":\"Nachgeschärft\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.body").value("Nachgeschärft"));

    // Mitglied liest den Aktivitätsverlauf (CREATED beim Anlegen + UPDATED + ASSIGNED beim
    // Editieren).
    mvc.perform(get("/api/cards/" + ideaId + "/activity").cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));

    // Nichtmitglied ist an allen Editier-/Lese-Pfaden ausgesperrt — 404 (kein Existenz-Leak, s.
    // PermissionChecker: Nichtmitglieder erhalten 404, nicht 403).
    mvc.perform(
            patch("/api/cards/" + ideaId)
                .cookie(stranger)
                .contentType("application/json")
                .content("{\"title\":\"gekapert\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/cards/" + ideaId + "/assignees")
                .cookie(stranger)
                .contentType("application/json")
                .content("{\"assignees\":[]}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/cards/" + ideaId + "/comments")
                .cookie(stranger)
                .contentType("application/json")
                .content("{\"body\":\"x\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/cards/" + ideaId + "/activity").cookie(stranger))
        .andExpect(status().isNotFound());
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
