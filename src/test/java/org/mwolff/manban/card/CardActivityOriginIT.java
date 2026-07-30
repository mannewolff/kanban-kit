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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-End der Aktivitäts-Herkunft (#517): Session-Aktionen tragen {@code SESSION}, Aktionen über
 * den PAT-Ingest {@code TOKEN} samt Anzeigename des Tokens; die Modell-Selbstauskunft aus {@code
 * X-Agent-Model} wird übernommen, ohne Header bleibt sie leer. Die Kette läuft über die echten
 * Filter — sie sichert zugleich die Authority-Literale im {@code SecurityActorContext} ab.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CardActivityOriginIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  @Test
  void sessionAndTokenActionsCarryTheirOrigin() throws Exception {
    Cookie owner = session("origin-owner@example.com", PlatformRole.USER);
    Cookie admin = session("origin-admin@example.com", PlatformRole.ADMIN);
    long projectId = createProject(admin, "Projekt", "origin-owner@example.com");
    JsonNode board = createBoard(owner, projectId, "Board");
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();

    // Session-Aktion ohne Header: origin=SESSION, kein Token-Name, keine Modell-Angabe.
    long sessionCard = createCard(owner, boardId, columnId, "Session-Karte", null);
    mvc.perform(get("/api/cards/" + sessionCard + "/activity").cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].origin").value("SESSION"))
        .andExpect(jsonPath("$[0].tokenName").value(Matchers.nullValue()))
        .andExpect(jsonPath("$[0].agent").value(Matchers.nullValue()));

    // Session-Aktion mit Selbstauskunft: agent wird übernommen.
    long agentCard = createCard(owner, boardId, columnId, "Agent-Karte", "claude-fable-5");
    mvc.perform(get("/api/cards/" + agentCard + "/activity").cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].origin").value("SESSION"))
        .andExpect(jsonPath("$[0].agent").value("claude-fable-5"));

    // PAT-Ingest mit Selbstauskunft: origin=TOKEN samt Anzeigename des Tokens.
    String token = boundToken(owner, projectId, boardId, "Nachtlauf");
    String created =
        mvc.perform(
                post("/api/kanban/items")
                    .header("X-Kanban-Token", token)
                    .header("X-Agent-Model", "claude-opus-5")
                    .contentType("application/json")
                    .content("{\"title\":\"Nachtlauf-Idee\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long ideaId = json.readTree(created).get("id").asLong();

    mvc.perform(get("/api/cards/" + ideaId + "/activity").cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].origin").value("TOKEN"))
        .andExpect(jsonPath("$[0].tokenName").value("Nachtlauf"))
        .andExpect(jsonPath("$[0].agent").value("claude-opus-5"));
  }

  private String boundToken(Cookie session, long projectId, long boardId, String name)
      throws Exception {
    String body =
        mvc.perform(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType("application/json")
                    .content(
                        "{\"name\":\"%s\",\"projectId\":%d,\"boardId\":%d}"
                            .formatted(name, projectId, boardId)))
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

  private long createCard(
      Cookie session,
      long boardId,
      long columnId,
      String title,
      @org.jspecify.annotations.Nullable String agentHeader)
      throws Exception {
    var request =
        post("/api/boards/" + boardId + "/cards")
            .cookie(session)
            .contentType("application/json")
            .content("{\"columnId\":%d,\"title\":\"%s\"}".formatted(columnId, title));
    if (agentHeader != null) {
      request = request.header("X-Agent-Model", agentHeader);
    }
    return json.readTree(
            mvc.perform(request)
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
