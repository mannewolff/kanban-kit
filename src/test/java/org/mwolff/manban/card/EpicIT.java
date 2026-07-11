package org.mwolff.manban.card;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-End-Test für Epics: Anlegen, Kind-Zuordnung, Fortschritt, Positions-Isolation, Löschen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class EpicIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  private Cookie loginAs(String email) throws Exception {
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

  private long createProject(String ownerEmail, String name) throws Exception {
    if (users.findByEmail(ownerEmail).isEmpty()) {
      users.save(
          new AppUser(
              null,
              ownerEmail,
              passwordEncoder.encode(PASSWORD),
              "Person",
              true,
              PlatformRole.USER));
    }
    Cookie admin = platformAdminSession();
    String body =
        mvc.perform(
                post("/api/projects")
                    .cookie(admin)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\",\"ownerEmail\":\"%s\"}".formatted(name, ownerEmail)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private Cookie platformAdminSession() throws Exception {
    String email = "project-admin@example.com";
    if (users.findByEmail(email).isEmpty()) {
      users.save(
          new AppUser(
              null, email, passwordEncoder.encode(PASSWORD), "Person", true, PlatformRole.ADMIN));
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

  private JsonNode createBoard(Cookie session, long projectId) throws Exception {
    String body =
        mvc.perform(
                post("/api/projects/" + projectId + "/boards")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"B\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  private long createCard(Cookie session, long boardId, long columnId, String title)
      throws Exception {
    String body =
        mvc.perform(
                post("/api/boards/" + boardId + "/cards")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"columnId\":%d,\"title\":\"%s\"}".formatted(columnId, title)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private long createEpic(Cookie session, long boardId, String title, String shortcode)
      throws Exception {
    String body =
        mvc.perform(
                post("/api/boards/" + boardId + "/cards")
                    .cookie(session)
                    .contentType("application/json")
                    .content(
                        "{\"type\":\"EPIC\",\"title\":\"%s\",\"shortcode\":\"%s\"}"
                            .formatted(title, shortcode)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("EPIC"))
            .andExpect(jsonPath("$.shortcode").value(shortcode))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private void assignParent(Cookie session, long cardId, Long parentId) throws Exception {
    String parent = parentId == null ? "null" : parentId.toString();
    mvc.perform(
            patch("/api/cards/" + cardId + "/parent")
                .cookie(session)
                .contentType("application/json")
                .content("{\"parentId\":%s}".formatted(parent)))
        .andExpect(status().isOk());
  }

  private JsonNode epics(Cookie session, long boardId) throws Exception {
    return json.readTree(
        mvc.perform(get("/api/boards/" + boardId + "/epics").cookie(session))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode boardCards(Cookie session, long boardId) throws Exception {
    return json.readTree(
        mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(session))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void epicProgressReflectsChildrenInDone() throws Exception {
    Cookie alice = loginAs("epic-owner@example.com");
    long projectId = createProject("epic-owner@example.com", "P");
    JsonNode board = createBoard(alice, projectId);
    long boardId = board.get("id").asLong();
    long backlog = board.get("columns").get(0).get("id").asLong();
    long done = board.get("columns").get(4).get("id").asLong();

    long epic = createEpic(alice, boardId, "Auth-Epic", "AUTH");
    long c1 = createCard(alice, boardId, backlog, "A");
    long c2 = createCard(alice, boardId, backlog, "B");
    assignParent(alice, c1, epic);
    assignParent(alice, c2, epic);

    // Fortschritt anfangs 0/2
    JsonNode before = epics(alice, boardId);
    org.assertj.core.api.Assertions.assertThat(before.size()).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(before.get(0).get("total").asInt()).isEqualTo(2);
    org.assertj.core.api.Assertions.assertThat(before.get(0).get("done").asInt()).isZero();

    // Ein Kind nach Done -> 1/2
    mvc.perform(
            post("/api/cards/" + c1 + "/move")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"columnId\":%d,\"position\":0}".formatted(done)))
        .andExpect(status().isOk());

    JsonNode after = epics(alice, boardId);
    org.assertj.core.api.Assertions.assertThat(after.get(0).get("done").asInt()).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(after.get(0).get("total").asInt()).isEqualTo(2);

    // Epics erscheinen nicht in der Board-Kartenliste
    JsonNode cards = boardCards(alice, boardId);
    org.assertj.core.api.Assertions.assertThat(cards.size()).isEqualTo(2);
  }

  @Test
  void epicHoldsNoPositionSoCardsReindexCleanly() throws Exception {
    Cookie alice = loginAs("epic-pos@example.com");
    long projectId = createProject("epic-pos@example.com", "P");
    JsonNode board = createBoard(alice, projectId);
    long boardId = board.get("id").asLong();
    long backlog = board.get("columns").get(0).get("id").asLong();

    // Epic liegt technisch in der ersten Spalte, hält aber keine Position.
    createEpic(alice, boardId, "E", "E");
    createCard(alice, boardId, backlog, "A");
    createCard(alice, boardId, backlog, "B");

    JsonNode cards = boardCards(alice, boardId);
    org.assertj.core.api.Assertions.assertThat(cards.size()).isEqualTo(2);
    // Karten belegen lückenlos Position 0 und 1 — der Epic verschiebt nichts.
    java.util.List<Integer> positions = new java.util.ArrayList<>();
    cards.forEach(c -> positions.add(c.get("positionInColumn").asInt()));
    java.util.Collections.sort(positions);
    org.assertj.core.api.Assertions.assertThat(positions).containsExactly(0, 1);
  }

  @Test
  void deletingEpicKeepsChildrenButUnassigned() throws Exception {
    Cookie alice = loginAs("epic-del@example.com");
    long projectId = createProject("epic-del@example.com", "P");
    JsonNode board = createBoard(alice, projectId);
    long boardId = board.get("id").asLong();
    long backlog = board.get("columns").get(0).get("id").asLong();

    long epic = createEpic(alice, boardId, "E", "E");
    long child = createCard(alice, boardId, backlog, "A");
    assignParent(alice, child, epic);

    mvc.perform(delete("/api/cards/" + epic).cookie(alice)).andExpect(status().isNoContent());

    // Kind bleibt, Zuordnung ist gelöst; keine Epics mehr.
    JsonNode cards = boardCards(alice, boardId);
    org.assertj.core.api.Assertions.assertThat(cards.size()).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(cards.get(0).get("parentId").isNull()).isTrue();
    org.assertj.core.api.Assertions.assertThat(epics(alice, boardId).size()).isZero();
  }

  @Test
  void updateAssignsAndUnassignsEpicInOnePut() throws Exception {
    Cookie alice = loginAs("epic-upd@example.com");
    long projectId = createProject("epic-upd@example.com", "P");
    JsonNode board = createBoard(alice, projectId);
    long boardId = board.get("id").asLong();
    long backlog = board.get("columns").get(0).get("id").asLong();

    long epic = createEpic(alice, boardId, "E", "E");
    long child = createCard(alice, boardId, backlog, "A");

    // Zuordnung im selben PUT wie Titel/Beschreibung
    mvc.perform(
            patch("/api/cards/" + child)
                .cookie(alice)
                .contentType("application/json")
                .content("{\"title\":\"A2\",\"parentId\":%d}".formatted(epic)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("A2"))
        .andExpect(jsonPath("$.parentId").value((int) epic));

    // parentId=null löst die Zuordnung
    String unassignedBody =
        mvc.perform(
                patch("/api/cards/" + child)
                    .cookie(alice)
                    .contentType("application/json")
                    .content("{\"title\":\"A2\",\"parentId\":null}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    org.assertj.core.api.Assertions.assertThat(
            JsonPath.parse(unassignedBody).<Object>read("$.parentId"))
        .isNull();
  }

  @Test
  void epicsAreNotMovableOnTheBoard() throws Exception {
    Cookie alice = loginAs("epic-move@example.com");
    long projectId = createProject("epic-move@example.com", "P");
    JsonNode board = createBoard(alice, projectId);
    long boardId = board.get("id").asLong();
    long done = board.get("columns").get(4).get("id").asLong();

    long epic = createEpic(alice, boardId, "E", "E");
    mvc.perform(
            post("/api/cards/" + epic + "/move")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"columnId\":%d,\"position\":0}".formatted(done)))
        .andExpect(status().isBadRequest());
  }
}
