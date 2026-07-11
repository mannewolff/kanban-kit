package org.mwolff.manban.card;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.Assertions;
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

/** End-to-End-Test für Karten-Move, kollisionsfreien Reindex und moved_to_done_at. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class CardMoveIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  private Cookie login;
  private long boardId;
  private long backlog;
  private long inProgress;
  private long done;

  private Cookie loginAs(String email) throws Exception {
    if (users.findByEmail(email).isEmpty()) {
      users.save(
          new AppUser(null, email, passwordEncoder.encode(PASSWORD), "P", true, PlatformRole.USER));
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

  private void setup(String email) throws Exception {
    login = loginAs(email);
    Cookie admin = platformAdminSession();
    String p =
        mvc.perform(
                post("/api/projects")
                    .cookie(admin)
                    .contentType("application/json")
                    .content("{\"name\":\"P\",\"ownerEmail\":\"%s\"}".formatted(email)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long projectId = json.readTree(p).get("id").asLong();
    JsonNode board =
        json.readTree(
            mvc.perform(
                    post("/api/projects/" + projectId + "/boards")
                        .cookie(login)
                        .contentType("application/json")
                        .content("{\"name\":\"B\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString());
    boardId = board.get("id").asLong();
    // Default-Spalten: [0]=Backlog, [1]=Ready, [2]=In Progress, [3]=In Review, [4]=Done
    backlog = board.get("columns").get(0).get("id").asLong();
    inProgress = board.get("columns").get(2).get("id").asLong();
    done = board.get("columns").get(4).get("id").asLong();
  }

  private int createCard(long columnId, String title) throws Exception {
    String body =
        mvc.perform(
                post("/api/boards/" + boardId + "/cards")
                    .cookie(login)
                    .contentType("application/json")
                    .content("{\"columnId\":%d,\"title\":\"%s\"}".formatted(columnId, title)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("number").asInt();
  }

  private void move(long cardId, long columnId, int position) throws Exception {
    mvc.perform(
            post("/api/cards/" + cardId + "/move")
                .cookie(login)
                .contentType("application/json")
                .content("{\"columnId\":%d,\"position\":%d}".formatted(columnId, position)))
        .andExpect(status().isOk());
  }

  private Map<Integer, JsonNode> cardsByNumber() throws Exception {
    String body =
        mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(login))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Map<Integer, JsonNode> map = new HashMap<>();
    json.readTree(body).forEach(c -> map.put(c.get("number").asInt(), c));
    return map;
  }

  private long cardId(int number) throws Exception {
    return cardsByNumber().get(number).get("id").asLong();
  }

  @Test
  void moveWithinColumnReindexes() throws Exception {
    setup("mv-within@example.com");
    createCard(backlog, "A"); // #1 pos0
    createCard(backlog, "B"); // #2 pos1
    createCard(backlog, "C"); // #3 pos2

    move(cardId(3), backlog, 0); // C an den Anfang

    Map<Integer, JsonNode> cards = cardsByNumber();
    Assertions.assertThat(cards.get(3).get("positionInColumn").asInt()).isZero();
    Assertions.assertThat(cards.get(1).get("positionInColumn").asInt()).isEqualTo(1);
    Assertions.assertThat(cards.get(2).get("positionInColumn").asInt()).isEqualTo(2);
  }

  @Test
  void moveAcrossColumnsReindexesBoth() throws Exception {
    setup("mv-across@example.com");
    createCard(backlog, "A"); // #1
    createCard(backlog, "B"); // #2
    createCard(backlog, "C"); // #3

    move(cardId(1), inProgress, 0); // A -> In Progress

    Map<Integer, JsonNode> cards = cardsByNumber();
    Assertions.assertThat(cards.get(1).get("columnId").asLong()).isEqualTo(inProgress);
    Assertions.assertThat(cards.get(1).get("positionInColumn").asInt()).isZero();
    // Backlog lückenlos reindiziert: B=0, C=1
    Assertions.assertThat(cards.get(2).get("positionInColumn").asInt()).isZero();
    Assertions.assertThat(cards.get(3).get("positionInColumn").asInt()).isEqualTo(1);
  }

  @Test
  void moveToDoneSetsTimestampAndLeavingClears() throws Exception {
    setup("mv-done@example.com");
    createCard(backlog, "A"); // #1

    move(cardId(1), done, 0);
    Assertions.assertThat(cardsByNumber().get(1).get("movedToDoneAt").isNull()).isFalse();

    move(cardId(1), backlog, 0);
    Assertions.assertThat(cardsByNumber().get(1).get("movedToDoneAt").isNull()).isTrue();
  }

  @Test
  void repeatedMovesCauseNoConstraintConflict() throws Exception {
    setup("mv-stress@example.com");
    for (int i = 0; i < 4; i++) {
      createCard(backlog, "C" + i); // #1..#4, pos 0..3
    }
    // Eine Folge von Moves, die die klassische 409-Reindex-Falle auslösen würde.
    move(cardId(4), backlog, 0);
    move(cardId(1), backlog, 3);
    move(cardId(2), inProgress, 0);
    move(cardId(2), backlog, 1);
    move(cardId(3), inProgress, 0);

    // Positionen je Spalte müssen eine lückenlose Permutation 0..n-1 sein.
    Map<Integer, JsonNode> cards = cardsByNumber();
    Map<Long, java.util.List<Integer>> byColumn = new HashMap<>();
    cards
        .values()
        .forEach(
            c ->
                byColumn
                    .computeIfAbsent(c.get("columnId").asLong(), k -> new java.util.ArrayList<>())
                    .add(c.get("positionInColumn").asInt()));
    byColumn
        .values()
        .forEach(
            positions -> {
              java.util.List<Integer> sorted = positions.stream().sorted().toList();
              for (int i = 0; i < sorted.size(); i++) {
                Assertions.assertThat(sorted.get(i)).isEqualTo(i);
              }
            });
  }

  @Test
  void archivedCardDoesNotBlockMoveOrDelete() throws Exception {
    setup("mv-arch@example.com");
    createCard(backlog, "A"); // #1 pos0
    createCard(backlog, "B"); // #2 pos1

    mvc.perform(post("/api/cards/" + cardId(1) + "/archive").cookie(login))
        .andExpect(status().isOk());

    // B an Position 0 verschieben (wo die archivierte A lag) -> kein Konflikt.
    move(cardId(2), backlog, 0);
    Assertions.assertThat(cardsByNumber().get(2).get("positionInColumn").asInt()).isZero();

    // Force-Delete der archivierten Karte -> kein Konflikt.
    mvc.perform(delete("/api/cards/" + cardId(1)).cookie(login)).andExpect(status().isNoContent());
  }
}
