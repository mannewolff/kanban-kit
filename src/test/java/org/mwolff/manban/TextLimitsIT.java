package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.TextLimits;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Textgrenze für Beschreibungen und Kommentare über alle Schreibwege (Issue #572).
 *
 * <p>Die Grenze war vorher uneinheitlich: Kommentare 10.000, Beschreibungen serverseitig gar
 * nichts. Dieser Test hält beides an einer Zahl fest — und zwar an jedem Endpunkt, der Text
 * entgegennimmt, weil eine einzige unbeschränkte Route die Zusage der Oberfläche wertlos macht.
 *
 * <p>Geprüft wird je Weg: {@link TextLimits#MAX_TEXT} Zeichen kommen vollständig an (der aus der
 * Datenbank gelesene Wert hat exakt diese Länge), ein Zeichen mehr ergibt 400 und verändert nichts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TextLimitsIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final String AT_LIMIT = "a".repeat(TextLimits.MAX_TEXT);
  private static final String OVER_LIMIT = "a".repeat(TextLimits.MAX_TEXT + 1);

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  private Cookie session;
  private long projectId;
  private long boardId;
  private long columnId;
  private String token;

  @BeforeEach
  void setUpFixture() throws Exception {
    session = login("limits-owner@example.com", PlatformRole.USER);
    Cookie admin = login("limits-admin@example.com", PlatformRole.ADMIN);
    projectId = createProject(admin, "Limits-Projekt", "limits-owner@example.com");
    JsonNode board = createBoard(projectId, "Limits-Board");
    boardId = board.get("id").asLong();
    columnId = board.get("columns").get(0).get("id").asLong();
    token = boundToken(projectId, boardId);
  }

  @Test
  void cardDescriptionAcceptsTheLimitAndRejectsOneMore() throws Exception {
    // POST /api/boards/{boardId}/cards
    long cardId = createCard(AT_LIMIT);
    assertThat(descriptionOf(cardId)).hasSize(TextLimits.MAX_TEXT);

    long before = cardCount();
    mvc.perform(
            post("/api/boards/" + boardId + "/cards")
                .cookie(session)
                .contentType("application/json")
                .content(cardPayload("Zu lang", OVER_LIMIT)))
        .andExpect(status().isBadRequest());
    assertThat(cardCount()).isEqualTo(before);

    // PATCH /api/cards/{cardId}
    mvc.perform(
            patch("/api/cards/" + cardId)
                .cookie(session)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("title", "Neu", "description", AT_LIMIT))))
        .andExpect(status().isOk());
    assertThat(descriptionOf(cardId)).hasSize(TextLimits.MAX_TEXT);

    mvc.perform(
            patch("/api/cards/" + cardId)
                .cookie(session)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(Map.of("title", "Neu", "description", OVER_LIMIT))))
        .andExpect(status().isBadRequest());
    // Der abgelehnte Änderungsversuch lässt den bisherigen Inhalt stehen.
    assertThat(descriptionOf(cardId)).hasSize(TextLimits.MAX_TEXT);
  }

  @Test
  void ideaEndpointsAcceptTheLimitAndRejectOneMore() throws Exception {
    // POST /api/projects/{projectId}/ideas — vor #572 komplett unbegrenzt.
    long ideaId =
        json.readTree(
                mvc.perform(
                        post("/api/projects/" + projectId + "/ideas")
                            .cookie(session)
                            .contentType("application/json")
                            .content(
                                json.writeValueAsString(
                                    Map.of("title", "Idee", "description", AT_LIMIT))))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();
    assertThat(descriptionOf(ideaId)).hasSize(TextLimits.MAX_TEXT);

    long before = cardCount();
    mvc.perform(
            post("/api/projects/" + projectId + "/ideas")
                .cookie(session)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(Map.of("title", "Idee", "description", OVER_LIMIT))))
        .andExpect(status().isBadRequest());
    assertThat(cardCount()).isEqualTo(before);

    // POST /api/projects/{projectId}/ideas/batch — ein zu langes Element kippt den ganzen Stapel.
    batchRequest("Stapel", AT_LIMIT).andExpect(status().isCreated());
    before = cardCount();
    batchRequest("Stapel zu lang", OVER_LIMIT).andExpect(status().isBadRequest());
    assertThat(cardCount()).isEqualTo(before);
  }

  @Test
  void commentEndpointsAcceptTheLimitAndRejectOneMore() throws Exception {
    long cardId = createCard("Karte für Kommentare");

    // POST /api/cards/{cardId}/comments
    long commentId =
        json.readTree(
                commentRequest(cardId, AT_LIMIT)
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();
    assertThat(commentBodyOf(commentId)).hasSize(TextLimits.MAX_TEXT);

    long before = commentCount();
    commentRequest(cardId, OVER_LIMIT).andExpect(status().isBadRequest());
    assertThat(commentCount()).isEqualTo(before);

    // PATCH /api/comments/{commentId}
    mvc.perform(
            patch("/api/comments/" + commentId)
                .cookie(session)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("body", AT_LIMIT))))
        .andExpect(status().isOk());
    mvc.perform(
            patch("/api/comments/" + commentId)
                .cookie(session)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("body", OVER_LIMIT))))
        .andExpect(status().isBadRequest());
    assertThat(commentBodyOf(commentId)).hasSize(TextLimits.MAX_TEXT);

    // POST /api/kanban/items/{id}/comments
    mvc.perform(
            post("/api/kanban/items/" + cardId + "/comments")
                .header("X-Kanban-Token", token)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("body", AT_LIMIT))))
        .andExpect(status().isCreated());
    before = commentCount();
    mvc.perform(
            post("/api/kanban/items/" + cardId + "/comments")
                .header("X-Kanban-Token", token)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("body", OVER_LIMIT))))
        .andExpect(status().isBadRequest());
    assertThat(commentCount()).isEqualTo(before);
  }

  @Test
  void kanbanCompatEndpointsAcceptTheLimitAndRejectOneMore() throws Exception {
    // POST /api/kanban/items
    long ingested =
        json.readTree(
                mvc.perform(
                        post("/api/kanban/items")
                            .header("X-Kanban-Token", token)
                            .contentType("application/json")
                            .content(
                                json.writeValueAsString(
                                    Map.of("title", "Ingest", "body", AT_LIMIT, "direct", true))))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();
    assertThat(descriptionOf(ingested)).hasSize(TextLimits.MAX_TEXT);

    long before = cardCount();
    mvc.perform(
            post("/api/kanban/items")
                .header("X-Kanban-Token", token)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("title", "Ingest zu lang", "body", OVER_LIMIT, "direct", true))))
        .andExpect(status().isBadRequest());
    assertThat(cardCount()).isEqualTo(before);

    // PUT /api/kanban/items/{id}
    mvc.perform(
            put("/api/kanban/items/" + ingested)
                .header("X-Kanban-Token", token)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("title", "Neu", "body", AT_LIMIT))))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/kanban/items/" + ingested)
                .header("X-Kanban-Token", token)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("title", "Neu", "body", OVER_LIMIT))))
        .andExpect(status().isBadRequest());
    assertThat(descriptionOf(ingested)).hasSize(TextLimits.MAX_TEXT);
  }

  @Test
  void limitCountsUtf16CodeUnitsNotCodepoints() throws Exception {
    // Ein Emoji außerhalb der BMP belegt zwei UTF-16-Codeeinheiten. Die Hälfte der Grenze an
    // Emojis ist also genau erlaubt, eines mehr ist es nicht — das pinnt die Zählweise, die
    // Bean Validation ohnehin verwendet, gegen die abweichende Codepoint-Zählung von PostgreSQL.
    String emojiAtLimit = "😀".repeat(TextLimits.MAX_TEXT / 2);
    assertThat(emojiAtLimit).hasSize(TextLimits.MAX_TEXT);

    long cardId = createCard(emojiAtLimit);
    assertThat(descriptionOf(cardId)).hasSize(TextLimits.MAX_TEXT);

    long before = cardCount();
    mvc.perform(
            post("/api/boards/" + boardId + "/cards")
                .cookie(session)
                .contentType("application/json")
                .content(cardPayload("Ein Emoji zu viel", emojiAtLimit + "😀")))
        .andExpect(status().isBadRequest());
    assertThat(cardCount()).isEqualTo(before);
  }

  // --- Helfer ---------------------------------------------------------------

  private String cardPayload(String title, String description) throws Exception {
    return json.writeValueAsString(
        Map.of("title", title, "description", description, "columnId", columnId));
  }

  private ResultActions batchRequest(String title, String description) throws Exception {
    return mvc.perform(
        post("/api/projects/" + projectId + "/ideas/batch")
            .cookie(session)
            .contentType("application/json")
            .content(
                json.writeValueAsString(
                    Map.of("ideas", List.of(Map.of("title", title, "description", description))))));
  }

  private ResultActions commentRequest(long cardId, String body) throws Exception {
    return mvc.perform(
        post("/api/cards/" + cardId + "/comments")
            .cookie(session)
            .contentType("application/json")
            .content(json.writeValueAsString(Map.of("body", body))));
  }

  private long createCard(String description) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/boards/" + boardId + "/cards")
                        .cookie(session)
                        .contentType("application/json")
                        .content(cardPayload("Karte", description)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private String descriptionOf(long cardId) {
    return jdbc.queryForObject("SELECT description FROM card WHERE id = ?", String.class, cardId);
  }

  private String commentBodyOf(long commentId) {
    return jdbc.queryForObject("SELECT body FROM comment WHERE id = ?", String.class, commentId);
  }

  private long cardCount() {
    Long value =
        jdbc.queryForObject(
            "SELECT count(*) FROM card WHERE project_id = ?", Long.class, projectId);
    return value == null ? 0 : value;
  }

  private long commentCount() {
    Long value = jdbc.queryForObject("SELECT count(*) FROM comment", Long.class);
    return value == null ? 0 : value;
  }

  private String boundToken(long projectId, long boardId) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/access-tokens")
                        .cookie(session)
                        .contentType("application/json")
                        .content(
                            "{\"name\":\"limits\",\"projectId\":%d,\"boardId\":%d}"
                                .formatted(projectId, boardId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("plaintext")
        .asText();
  }

  private long createProject(Cookie admin, String name, String ownerEmail) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/projects")
                        .cookie(admin)
                        .contentType("application/json")
                        .content(
                            "{\"name\":\"%s\",\"ownerEmail\":\"%s\"}".formatted(name, ownerEmail)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private JsonNode createBoard(long projectId, String name) throws Exception {
    return json.readTree(
        mvc.perform(
                post("/api/projects/" + projectId + "/boards")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\"}".formatted(name)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private Cookie login(String email, PlatformRole role) throws Exception {
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
