package org.mwolff.manban.kanbancompat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-End des Body-Updates über kanbancompat (#571): {@code PUT /api/kanban/items/{id}} ersetzt
 * Titel und Beschreibung einer Karte des gebundenen Boards — und nichts sonst.
 *
 * <p>Der Kern des Tests sind die Invarianten: Der bestehende {@code CardService.update} ist kein
 * partielles Update und würde bei einem naiven Durchgriff mit {@code null} die Epic-Zuordnung, das
 * Fälligkeitsdatum und (bei Epics) das Kürzel löschen. Genau das pinnen die Prüfungen unten.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class KanbanCompatUpdateIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  /**
   * Umlaute, Zeilenumbrüche und ein Markdown-Codeblock — der Body des Kit-Adapters sieht so aus.
   */
  private static final String RICH_BODY =
      """
      ## Kontext
      Änderung mit Umlauten: ä ö ü ß — und einem Codeblock:

      ```java
      var x = "test";
      ```

      Letzte Zeile.""";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void updateReplacesTitleAndBodyAndLeavesEverythingElseUntouched() throws Exception {
    Fixture f = fixture("update-plain");
    long epicId = createEpic(f, "Sammel-Epic", "SAM");
    long predecessorId = directIngest(f.token, "Vorgänger", "egal", null);
    long cardId = directIngest(f.token, "Alter Titel", "vorher", "github#42");

    // Epic-Zuordnung, Fälligkeit und Abhängigkeiten setzen — sie dürfen das Update überleben.
    mvc.perform(
            patch("/api/cards/" + cardId)
                .cookie(f.session)
                .contentType("application/json")
                .content(
                    """
                    {"title":"Alter Titel","description":"vorher","parentId":%d,\
                    "dueDate":"2026-12-24T00:00:00Z","dependencies":[%d]}"""
                        .formatted(epicId, numberOf(predecessorId))))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/kanban/items/" + cardId + "/comments")
                .header("X-Kanban-Token", f.token)
                .contentType("application/json")
                .content("{\"body\":\"Ein Kommentar\"}"))
        .andExpect(status().isCreated());

    int number = numberOf(cardId);
    long columnBefore = columnOf(cardId);
    int positionBefore = positionOf(cardId);
    int activitiesBefore = updateActivities(cardId);

    mvc.perform(updateRequest(f.token, cardId, "Neuer Titel", RICH_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(cardId))
        .andExpect(jsonPath("$.number").value(number))
        .andExpect(jsonPath("$.title").value("Neuer Titel"))
        .andExpect(jsonPath("$.body").value(RICH_BODY));

    // Zeichengleich zurückgelesen — inklusive Umlauten, Umbrüchen und Codeblock.
    JsonNode item = itemByNumber(f.token, number);
    assertThat(item.get("title").asText()).isEqualTo("Neuer Titel");
    assertThat(item.get("body").asText()).isEqualTo(RICH_BODY);

    // Alles andere unverändert.
    assertThat(numberOf(cardId)).isEqualTo(number);
    assertThat(columnOf(cardId)).isEqualTo(columnBefore);
    assertThat(positionOf(cardId)).isEqualTo(positionBefore);
    assertThat(externalKeyOf(cardId)).isEqualTo("github#42");
    assertThat(parentOf(cardId)).isEqualTo(epicId);
    assertThat(dueDateCount(cardId)).isEqualTo(1);
    assertThat(dependencyCount(cardId)).isEqualTo(1);
    assertThat(updateActivities(cardId)).isEqualTo(activitiesBefore + 1);

    mvc.perform(get("/api/kanban/items/" + cardId + "/comments").header("X-Kanban-Token", f.token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].body").value("Ein Kommentar"));
  }

  @Test
  void updateKeepsEpicShortcodeAndType() throws Exception {
    Fixture f = fixture("update-epic");
    long epicId = createEpic(f, "Altes Epic", "EPX");

    mvc.perform(updateRequest(f.token, epicId, "Neues Epic", "neuer Rumpf"))
        .andExpect(status().isOk());

    mvc.perform(get("/api/kanban/epics").header("X-Kanban-Token", f.token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title").value("Neues Epic"))
        .andExpect(jsonPath("$[0].shortcode").value("EPX"));
    assertThat(typeOf(epicId)).isEqualTo("EPIC");
  }

  @Test
  void updateRejectsUnknownForeignAndPoolCards() throws Exception {
    Fixture f = fixture("update-scope");

    // Unbekannte id.
    mvc.perform(updateRequest(f.token, 999_999L, "Egal", "Egal")).andExpect(status().isNotFound());

    // Karte eines anderen Boards desselben Projekts — kein Existenz-Leak.
    long otherBoard = createBoard(f.session, f.projectId, "Anderes Board");
    long foreignCard = createCard(f.session, otherBoard, "Fremd");
    mvc.perform(updateRequest(f.token, foreignCard, "Neu", "Neu")).andExpect(status().isNotFound());
    assertThat(titleOf(foreignCard)).isEqualTo("Fremd");

    // Board-lose Pool-Idee (Ingest ohne direct=true).
    long poolId = poolIngest(f.token, "Pool-Idee", "Pool-Rumpf");
    mvc.perform(updateRequest(f.token, poolId, "Neu", "Neu")).andExpect(status().isNotFound());
    assertThat(titleOf(poolId)).isEqualTo("Pool-Idee");
    assertThat(descriptionOf(poolId)).isEqualTo("Pool-Rumpf");
  }

  @Test
  void updateRequiresValidBoundToken() throws Exception {
    Fixture f = fixture("update-token");
    long cardId = directIngest(f.token, "Unberührt", "Rumpf", null);

    mvc.perform(
            put("/api/kanban/items/" + cardId)
                .contentType("application/json")
                .content("{\"title\":\"Neu\",\"body\":\"Neu\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(updateRequest("tk_bogus", cardId, "Neu", "Neu"))
        .andExpect(status().isUnauthorized());

    // Gültiges, aber an kein Board gebundenes Token: 409, nicht 401.
    String unbound =
        json.readTree(
                mvc.perform(
                        post("/api/access-tokens")
                            .cookie(f.session)
                            .contentType("application/json")
                            .content("{\"name\":\"unbound\"}"))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("plaintext")
            .asText();
    mvc.perform(updateRequest(unbound, cardId, "Neu", "Neu")).andExpect(status().isConflict());

    assertThat(titleOf(cardId)).isEqualTo("Unberührt");
    assertThat(descriptionOf(cardId)).isEqualTo("Rumpf");
  }

  @Test
  void updateValidatesTitle() throws Exception {
    Fixture f = fixture("update-title");
    long cardId = directIngest(f.token, "Original", "Rumpf", null);

    mvc.perform(
            put("/api/kanban/items/" + cardId)
                .header("X-Kanban-Token", f.token)
                .contentType("application/json")
                .content("{\"body\":\"Ohne Titel\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(updateRequest(f.token, cardId, "   ", "Blanker Titel"))
        .andExpect(status().isBadRequest());
    mvc.perform(updateRequest(f.token, cardId, "x".repeat(301), "Zu lang"))
        .andExpect(status().isBadRequest());
    assertThat(titleOf(cardId)).isEqualTo("Original");

    // Die Grenze selbst wird angenommen.
    mvc.perform(updateRequest(f.token, cardId, "x".repeat(300), "Genau 300"))
        .andExpect(status().isOk());
    assertThat(titleOf(cardId)).isEqualTo("x".repeat(300));
  }

  @Test
  void updateTreatsMissingBodyAsUnchangedAndBlankBodyAsCleared() throws Exception {
    Fixture f = fixture("update-body");
    long cardId = directIngest(f.token, "Titel", "Bestand", null);

    // Fehlendes Feld: Beschreibung bleibt stehen.
    mvc.perform(
            put("/api/kanban/items/" + cardId)
                .header("X-Kanban-Token", f.token)
                .contentType("application/json")
                .content("{\"title\":\"Titel A\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.body").value("Bestand"));
    assertThat(descriptionOf(cardId)).isEqualTo("Bestand");

    // Explizites null: ebenfalls unverändert — ein weggelassener Wert vernichtet keinen Inhalt.
    mvc.perform(
            put("/api/kanban/items/" + cardId)
                .header("X-Kanban-Token", f.token)
                .contentType("application/json")
                .content("{\"title\":\"Titel B\",\"body\":null}"))
        .andExpect(status().isOk());
    assertThat(descriptionOf(cardId)).isEqualTo("Bestand");

    // Blanker Body: löscht die Beschreibung.
    mvc.perform(updateRequest(f.token, cardId, "Titel C", "   ")).andExpect(status().isOk());
    assertThat(descriptionOf(cardId)).isNull();
  }

  // --- Helfer ---------------------------------------------------------------

  private org.springframework.test.web.servlet.RequestBuilder updateRequest(
      String token, long cardId, String title, String body) throws Exception {
    return put("/api/kanban/items/" + cardId)
        .header("X-Kanban-Token", token)
        .contentType("application/json")
        .content(json.writeValueAsString(Map.of("title", title, "body", body)));
  }

  private JsonNode itemByNumber(String token, int number) throws Exception {
    JsonNode grouped =
        json.readTree(
            mvc.perform(get("/api/kanban/items").header("X-Kanban-Token", token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    for (JsonNode column : grouped) {
      for (JsonNode item : column) {
        if (item.get("number").asInt() == number) {
          return item;
        }
      }
    }
    throw new AssertionError("Karte #" + number + " nicht in den Items gefunden");
  }

  private long directIngest(String token, String title, String body, String externalKey)
      throws Exception {
    String key = externalKey == null ? "" : ",\"externalKey\":\"%s\"".formatted(externalKey);
    String created =
        mvc.perform(
                post("/api/kanban/items")
                    .header("X-Kanban-Token", token)
                    .contentType("application/json")
                    .content(
                        "{\"title\":\"%s\",\"body\":\"%s\",\"direct\":true%s}"
                            .formatted(title, body, key)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(created).get("id").asLong();
  }

  private long poolIngest(String token, String title, String body) throws Exception {
    String created =
        mvc.perform(
                post("/api/kanban/items")
                    .header("X-Kanban-Token", token)
                    .contentType("application/json")
                    .content("{\"title\":\"%s\",\"body\":\"%s\"}".formatted(title, body)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(created).get("id").asLong();
  }

  private long createEpic(Fixture f, String title, String shortcode) throws Exception {
    String created =
        mvc.perform(
                post("/api/boards/" + f.boardId + "/cards")
                    .cookie(f.session)
                    .contentType("application/json")
                    .content(
                        "{\"title\":\"%s\",\"type\":\"EPIC\",\"shortcode\":\"%s\"}"
                            .formatted(title, shortcode)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(created).get("id").asLong();
  }

  private long createCard(Cookie session, long boardId, String title) throws Exception {
    long columnId =
        json.readTree(
                mvc.perform(get("/api/boards/" + boardId).cookie(session))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("columns")
            .get(0)
            .get("id")
            .asLong();
    String created =
        mvc.perform(
                post("/api/boards/" + boardId + "/cards")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"title\":\"%s\",\"columnId\":%d}".formatted(title, columnId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(created).get("id").asLong();
  }

  private String titleOf(long cardId) {
    return jdbc.queryForObject("SELECT title FROM card WHERE id = ?", String.class, cardId);
  }

  private String descriptionOf(long cardId) {
    return jdbc.queryForObject("SELECT description FROM card WHERE id = ?", String.class, cardId);
  }

  private String typeOf(long cardId) {
    return jdbc.queryForObject("SELECT type FROM card WHERE id = ?", String.class, cardId);
  }

  private String externalKeyOf(long cardId) {
    return jdbc.queryForObject("SELECT external_key FROM card WHERE id = ?", String.class, cardId);
  }

  private int numberOf(long cardId) {
    return count("SELECT number FROM card WHERE id = ?", cardId);
  }

  private long columnOf(long cardId) {
    Long value = jdbc.queryForObject("SELECT column_id FROM card WHERE id = ?", Long.class, cardId);
    return value == null ? -1 : value;
  }

  private int positionOf(long cardId) {
    return count("SELECT position_in_column FROM card WHERE id = ?", cardId);
  }

  private Long parentOf(long cardId) {
    return jdbc.queryForObject("SELECT parent_id FROM card WHERE id = ?", Long.class, cardId);
  }

  private int dueDateCount(long cardId) {
    return count("SELECT count(*) FROM card WHERE id = ? AND due_date IS NOT NULL", cardId);
  }

  private int dependencyCount(long cardId) {
    return count("SELECT count(*) FROM card_dependency WHERE card_id = ?", cardId);
  }

  private int updateActivities(long cardId) {
    return count(
        "SELECT count(*) FROM card_activity WHERE card_id = ? AND type = 'UPDATED'", cardId);
  }

  private int count(String sql, long cardId) {
    Integer value = jdbc.queryForObject(sql, Integer.class, cardId);
    return value == null ? 0 : value;
  }

  private record Fixture(Cookie session, long projectId, long boardId, String token) {}

  private Fixture fixture(String prefix) throws Exception {
    Cookie owner = session(prefix + "-owner@example.com", PlatformRole.USER);
    Cookie admin = session(prefix + "-admin@example.com", PlatformRole.ADMIN);
    long projectId = createProject(admin, "Projekt " + prefix, prefix + "-owner@example.com");
    long boardId = createBoard(owner, projectId, "Board " + prefix);
    return new Fixture(owner, projectId, boardId, boundToken(owner, projectId, boardId));
  }

  private String boundToken(Cookie session, long projectId, long boardId) throws Exception {
    String body =
        mvc.perform(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType("application/json")
                    .content(
                        "{\"name\":\"sync-token\",\"projectId\":%d,\"boardId\":%d}"
                            .formatted(projectId, boardId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("plaintext").asText();
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

  private long createBoard(Cookie session, long projectId, String name) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/projects/" + projectId + "/boards")
                        .cookie(session)
                        .contentType("application/json")
                        .content("{\"name\":\"%s\"}".formatted(name)))
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
