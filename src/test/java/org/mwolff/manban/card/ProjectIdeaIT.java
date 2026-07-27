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

    // Board-lose Idee im Pool anlegen (mit notiertem Zielboard). Sie bekommt sofort eine
    // projektweite Nummer (#402).
    var ideaNode =
        json.readTree(
            mvc.perform(
                    post("/api/projects/" + projectId + "/ideas")
                        .cookie(owner)
                        .contentType("application/json")
                        .content("{\"title\":\"Idee A\",\"targetBoardId\":" + boardId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.boardId").value(nullValue()))
                .andExpect(jsonPath("$.number").isNumber())
                .andExpect(jsonPath("$.ideaStored").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString());
    long ideaId = ideaNode.get("id").asLong();
    int ideaNumber = ideaNode.get("number").asInt();

    // Taucht in der Projekt-Ideen-Liste (mit Nummer) auf, aber nicht in den Board-Karten.
    mvc.perform(get("/api/projects/" + projectId + "/ideas").cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(ideaId))
        .andExpect(jsonPath("$[0].number").value(ideaNumber));
    mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(owner))
        .andExpect(jsonPath("$.length()").value(0));

    // Einplanen -> landet im Board-Backlog (board-gebunden, nicht mehr Idee); die Nummer bleibt
    // dieselbe (keine Neuvergabe).
    mvc.perform(
            put("/api/cards/" + ideaId + "/plan")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"targetBoardId\":" + boardId + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.boardId").value(boardId))
        .andExpect(jsonPath("$.number").value(ideaNumber))
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

  /**
   * Der Pool liefert die älteste Idee zuerst (#419), und wer ihn in dieser Reihenfolge einplant,
   * findet sie im Backlog in derselben Reihenfolge wieder. Vorher lief der Pool absteigend, während
   * das Backlog aufsteigend sortiert ist — beim Abarbeiten von oben nach unten kehrte sich die
   * Reihenfolge dadurch um.
   */
  @Test
  void pool_lists_oldest_first_and_planning_in_that_order_preserves_it() throws Exception {
    Cookie owner = session("idea-order-owner@example.com", PlatformRole.USER);
    Cookie admin = session("idea-order-admin@example.com", PlatformRole.ADMIN);

    long projectId = createProject(admin, "idea-order-owner@example.com");
    long boardId = createBoard(owner, projectId);

    long first = createIdea(owner, projectId, "Idee A");
    long second = createIdea(owner, projectId, "Idee B");
    long third = createIdea(owner, projectId, "Idee C");

    // Älteste zuerst; die projektweiten Nummern steigen entsprechend an.
    mvc.perform(get("/api/projects/" + projectId + "/ideas").cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].id").value(first))
        .andExpect(jsonPath("$[1].id").value(second))
        .andExpect(jsonPath("$[2].id").value(third))
        .andExpect(jsonPath("$[0].title").value("Idee A"))
        .andExpect(jsonPath("$[2].title").value("Idee C"));

    // In Listenreihenfolge einplanen — jede Karte landet am Ende der ersten Spalte.
    for (long ideaId : new long[] {first, second, third}) {
      mvc.perform(
              put("/api/cards/" + ideaId + "/plan")
                  .cookie(owner)
                  .contentType("application/json")
                  .content("{\"targetBoardId\":" + boardId + "}"))
          .andExpect(status().isOk());
    }

    // Im Backlog stehen sie in derselben Reihenfolge, mit aufsteigenden Positionen.
    mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[?(@.id == " + first + ")].positionInColumn").value(0))
        .andExpect(jsonPath("$[?(@.id == " + second + ")].positionInColumn").value(1))
        .andExpect(jsonPath("$[?(@.id == " + third + ")].positionInColumn").value(2));
  }

  private long createProject(Cookie admin, String ownerEmail) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/projects")
                        .cookie(admin)
                        .contentType("application/json")
                        .content("{\"name\":\"P\",\"ownerEmail\":\"%s\"}".formatted(ownerEmail)))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private long createBoard(Cookie owner, long projectId) throws Exception {
    return json.readTree(
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
  }

  private long createIdea(Cookie owner, long projectId, String title) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/projects/" + projectId + "/ideas")
                        .cookie(owner)
                        .contentType("application/json")
                        .content("{\"title\":\"%s\"}".formatted(title)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
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
