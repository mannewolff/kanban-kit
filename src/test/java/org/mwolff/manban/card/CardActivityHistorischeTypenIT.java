package org.mwolff.manban.card;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
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
 * Bestandsschutz des Aktivitätsverlaufs nach dem Rückbau des Ideen-Pools (Issue #1204, E17): {@code
 * card_activity} speichert den Typ als Text, und der Adapter liest ihn mit {@code
 * CardActivityType.valueOf(...)} ohne Rückfall. Jede Karte, die je durch den Pool gelaufen ist,
 * trägt Zeilen mit {@code IDEA_STORED} oder {@code PROMOTED} — fielen die beiden Konstanten mit
 * ihren Schreibstellen weg, antwortete {@code GET /api/cards/{id}/activity} für diese Karten mit
 * 500 statt mit ihrem Verlauf.
 *
 * <p>Die Zeilen werden bewusst per SQL geseedet: Die beiden Typen sind seit #1204 historische Werte
 * ohne Schreibpfad, sie lassen sich über die API nicht mehr erzeugen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CardActivityHistorischeTypenIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void verlaufMitPoolZeilenBleibtLesbar() throws Exception {
    Cookie owner = session("historie-owner@example.com", PlatformRole.USER);
    Cookie admin = session("historie-admin@example.com", PlatformRole.ADMIN);
    long projectId = createProject(admin, "Historie", "historie-owner@example.com");
    JsonNode board = createBoard(owner, projectId, "Board");
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();
    long cardId = createCard(owner, boardId, columnId, "Karte aus dem Pool");

    seedActivity(cardId, "IDEA_STORED", "In den Ideen-Speicher");
    seedActivity(cardId, "PROMOTED", "Auf Board eingeplant");

    mvc.perform(get("/api/cards/" + cardId + "/activity").cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(3)))
        .andExpect(jsonPath("$[0].type").value("CREATED"))
        .andExpect(jsonPath("$[1].type").value("IDEA_STORED"))
        .andExpect(jsonPath("$[1].detail").value("In den Ideen-Speicher"))
        .andExpect(jsonPath("$[2].type").value("PROMOTED"))
        .andExpect(jsonPath("$[2].detail").value("Auf Board eingeplant"));
  }

  private void seedActivity(long cardId, String type, String detail) {
    jdbc.update(
        "INSERT INTO card_activity (card_id, actor_user_id, type, detail, created_at) "
            + "VALUES (?, NULL, ?, ?, now())",
        cardId,
        type,
        detail);
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

  private long createCard(Cookie session, long boardId, long columnId, String title)
      throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/boards/" + boardId + "/cards")
                        .cookie(session)
                        .contentType("application/json")
                        .content("{\"columnId\":%d,\"title\":\"%s\"}".formatted(columnId, title)))
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
