package org.mwolff.manban.kanbancompat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-End der Ingest-Idempotenz über {@code externalKey} (#534): Wiederholte Ingests desselben
 * Schlüssels erzeugen genau eine Karte; auch eine in den Papierkorb verworfene Karte unterdrückt
 * die Neuanlage; der Schlüssel ist projekt-scoped.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class KanbanCompatExternalKeyIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void repeatedIngestWithSameKeyCreatesExactlyOneCard() throws Exception {
    Cookie owner = session("extkey-owner@example.com", PlatformRole.USER);
    Cookie admin = session("extkey-admin@example.com", PlatformRole.ADMIN);
    long projectId = createProject(admin, "Projekt", "extkey-owner@example.com");
    JsonNode board = createBoard(owner, projectId, "Board");
    String token = boundToken(owner, projectId, board.get("id").asLong());

    // Erst-Ingest: legt an, created=true.
    long firstId = ingest(token, "Finding A", "sonar:AAA", true);

    // Wiederholung: selbe Karte, created=false, keine zweite Zeile.
    long secondId = ingest(token, "Finding A (erneut)", "sonar:AAA", false);
    assertThat(secondId).isEqualTo(firstId);
    assertThat(countByKey(projectId, "sonar:AAA")).isEqualTo(1);

    // Auch eine verworfene Karte unterdrückt den Re-Ingest weiterhin: erst aufs Board einplanen
    // (Pool-Ideen sind board-los und kennen keinen Papierkorb-Weg), dann in den Papierkorb.
    mvc.perform(
            put("/api/cards/" + firstId + "/plan")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"targetBoardId\":%d}".formatted(board.get("id").asLong())))
        .andExpect(status().isOk());
    mvc.perform(delete("/api/cards/" + firstId).cookie(owner)).andExpect(status().isNoContent());
    long thirdId = ingest(token, "Finding A (nach Verwerfen)", "sonar:AAA", false);
    assertThat(thirdId).isEqualTo(firstId);
    assertThat(countByKey(projectId, "sonar:AAA")).isEqualTo(1);

    // Ohne Schlüssel bleibt der Ingest nicht-idempotent (zwei Karten wie bisher).
    long freeA = ingest(token, "Ohne Schlüssel", null, true);
    long freeB = ingest(token, "Ohne Schlüssel", null, true);
    assertThat(freeA).isNotEqualTo(freeB);
  }

  @Test
  void sameKeyInDifferentProjectsCreatesSeparateCards() throws Exception {
    Cookie owner = session("extkey2-owner@example.com", PlatformRole.USER);
    Cookie admin = session("extkey2-admin@example.com", PlatformRole.ADMIN);
    long projectA = createProject(admin, "Projekt A", "extkey2-owner@example.com");
    long projectB = createProject(admin, "Projekt B", "extkey2-owner@example.com");
    JsonNode boardA = createBoard(owner, projectA, "Board A");
    JsonNode boardB = createBoard(owner, projectB, "Board B");
    String tokenA = boundToken(owner, projectA, boardA.get("id").asLong());
    String tokenB = boundToken(owner, projectB, boardB.get("id").asLong());

    long idA = ingest(tokenA, "Finding", "sonar:SHARED", true);
    long idB = ingest(tokenB, "Finding", "sonar:SHARED", true);

    assertThat(idA).isNotEqualTo(idB);
    assertThat(countByKey(projectA, "sonar:SHARED")).isEqualTo(1);
    assertThat(countByKey(projectB, "sonar:SHARED")).isEqualTo(1);
  }

  private long ingest(String token, String title, String externalKey, boolean expectCreated)
      throws Exception {
    String key = externalKey == null ? "" : ",\"externalKey\":\"%s\"".formatted(externalKey);
    String body =
        mvc.perform(
                post("/api/kanban/items")
                    .header("X-Kanban-Token", token)
                    .contentType("application/json")
                    .content("{\"title\":\"%s\"%s}".formatted(title, key)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.created").value(expectCreated))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private int countByKey(long projectId, String key) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM card WHERE project_id = ? AND external_key = ?",
            Integer.class,
            projectId,
            key);
    return count == null ? 0 : count;
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

  private JsonNode createBoard(Cookie session, long projectId, String name) throws Exception {
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
