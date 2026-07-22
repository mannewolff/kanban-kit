package org.mwolff.manban.card;

import static org.hamcrest.Matchers.nullValue;
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

/** End-to-End des projektweiten Ideen-Pools: anlegen (board-los), einplanen, zurück in den Pool. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ProjectIdeaIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  @Test
  void idea_created_planned_onto_board_and_back_to_pool() throws Exception {
    Cookie owner = session("idea-owner@example.com", PlatformRole.USER);
    Cookie admin = session("idea-admin@example.com", PlatformRole.ADMIN);

    long projectId =
        json.readTree(
                mvc.perform(
                        post("/api/projects")
                            .cookie(admin)
                            .contentType("application/json")
                            .content("{\"name\":\"P\",\"ownerEmail\":\"idea-owner@example.com\"}"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();
    long boardId =
        json.readTree(
                mvc.perform(
                        post("/api/projects/" + projectId + "/boards")
                            .cookie(owner)
                            .contentType("application/json")
                            .content("{\"name\":\"B\"}"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();

    // Board-lose Idee im Pool anlegen (mit notiertem Zielboard).
    long ideaId =
        json.readTree(
                mvc.perform(
                        post("/api/projects/" + projectId + "/ideas")
                            .cookie(owner)
                            .contentType("application/json")
                            .content("{\"title\":\"Idee A\",\"targetBoardId\":" + boardId + "}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.boardId").value(nullValue()))
                    .andExpect(jsonPath("$.ideaStored").value(true))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();

    // Taucht in der Projekt-Ideen-Liste auf, aber nicht in den Board-Karten.
    mvc.perform(get("/api/projects/" + projectId + "/ideas").cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(ideaId));
    mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(owner))
        .andExpect(jsonPath("$.length()").value(0));

    // Einplanen -> landet im Board-Backlog (board-gebunden, nicht mehr Idee).
    mvc.perform(
            put("/api/cards/" + ideaId + "/plan")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"targetBoardId\":" + boardId + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.boardId").value(boardId))
        .andExpect(jsonPath("$.ideaStored").value(false));
    mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(owner))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(ideaId));
    mvc.perform(get("/api/projects/" + projectId + "/ideas").cookie(owner))
        .andExpect(jsonPath("$.length()").value(0));

    // Zurück in den Pool -> wieder board-los.
    mvc.perform(put("/api/cards/" + ideaId + "/to-pool").cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.boardId").value(nullValue()))
        .andExpect(jsonPath("$.ideaStored").value(true));
    mvc.perform(get("/api/projects/" + projectId + "/ideas").cookie(owner))
        .andExpect(jsonPath("$.length()").value(1));
    mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(owner))
        .andExpect(jsonPath("$.length()").value(0));
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
