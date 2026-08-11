package org.mwolff.manban.kanbancompat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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
 * Lesepfad für den Idempotenz-Schlüssel (Issue #573): {@code GET /api/kanban/items} liefert den
 * {@code externalKey} mit.
 *
 * <p>Der Schlüssel wird seit #534 gespeichert, war aber über keine Schnittstelle abrufbar. Ein
 * Migrationswerkzeug konnte damit zwar wiederanlaufen (der Ingest ist idempotent), aber nicht
 * abgleichen, welche Karten eines Projekts aus seinem Import stammen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class KanbanCompatExternalKeyReadIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  private Cookie session;
  private long projectId;
  private long boardId;
  private String token;

  @BeforeEach
  void setUpFixture() throws Exception {
    session = login("extread-owner@example.com", PlatformRole.USER);
    Cookie admin = login("extread-admin@example.com", PlatformRole.ADMIN);
    projectId = createProject(admin, "ExtRead-Projekt", "extread-owner@example.com");
    JsonNode board = createBoard(projectId, "ExtRead-Board");
    boardId = board.get("id").asLong();
    token = boundToken(projectId, boardId);
  }

  @Test
  void itemsExposeTheExternalKeyOfIngestedCards() throws Exception {
    long ingested = directIngest("Mit Schluessel", "  github#164  ");

    JsonNode item = itemById(ingested);
    // Normalisiert abgelegt (getrimmt) — geliefert wird genau der gespeicherte Wert.
    assertThat(item.get("externalKey").asText()).isEqualTo("github#164");
  }

  @Test
  void itemsExposeNullForCardsWithoutKey() throws Exception {
    // Karte über den UI-Pfad: kein Ingest, also kein Schlüssel.
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
    long plain =
        json.readTree(
                mvc.perform(
                        post("/api/boards/" + boardId + "/cards")
                            .cookie(session)
                            .contentType("application/json")
                            .content(
                                "{\"title\":\"Ohne Schluessel\",\"columnId\":%d}"
                                    .formatted(columnId)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();

    assertThat(itemById(plain).get("externalKey").isNull()).isTrue();

    // Blanker Schlüssel zählt als keiner (bestehende Normalisierung, #534).
    long blank = directIngest("Blanker Schluessel", "   ");
    assertThat(itemById(blank).get("externalKey").isNull()).isTrue();
  }

  @Test
  void bodyUpdateKeepsAndReturnsTheExternalKey() throws Exception {
    long ingested = directIngest("Vor dem Update", "github#42");

    mvc.perform(
            put("/api/kanban/items/" + ingested)
                .header("X-Kanban-Token", token)
                .contentType("application/json")
                .content("{\"title\":\"Neuer Titel\",\"body\":\"Neuer Rumpf\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.externalKey").value("github#42"));

    assertThat(itemById(ingested).get("externalKey").asText()).isEqualTo("github#42");
  }

  // --- Helfer ---------------------------------------------------------------

  private JsonNode itemById(long cardId) throws Exception {
    JsonNode grouped =
        json.readTree(
            mvc.perform(get("/api/kanban/items").header("X-Kanban-Token", token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    for (JsonNode column : grouped) {
      for (JsonNode item : column) {
        if (item.get("id").asLong() == cardId) {
          return item;
        }
      }
    }
    throw new AssertionError("Karte " + cardId + " nicht in den Items gefunden");
  }

  private long directIngest(String title, String externalKey) throws Exception {
    String created =
        mvc.perform(
                post("/api/kanban/items")
                    .header("X-Kanban-Token", token)
                    .contentType("application/json")
                    .content(
                        ("{\"title\":\"%s\",\"body\":\"egal\",\"direct\":true,"
                                + "\"externalKey\":\"%s\"}")
                            .formatted(title, externalKey)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(created).get("id").asLong();
  }

  private String boundToken(long projectId, long boardId) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/access-tokens")
                        .cookie(session)
                        .contentType("application/json")
                        .content(
                            "{\"name\":\"extread\",\"projectId\":%d,\"boardId\":%d}"
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
