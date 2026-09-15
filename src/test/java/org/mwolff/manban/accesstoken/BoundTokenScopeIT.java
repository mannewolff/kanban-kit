package org.mwolff.manban.accesstoken;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.accesstoken.application.AccessTokenRepository;
import org.mwolff.manban.accesstoken.domain.AccessToken;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.token.TokenCryptoPort;
import org.mwolff.manban.common.token.TokenCryptoPort.GeneratedToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Board-Grenze board-gebundener Tokens (#836/#877): Ein an ein Board gebundenes Token bedient
 * ausschließlich {@code /api/kanban/**} seines Boards; jeder andere {@code /api/**}-Zugriff endet
 * mit 403 — unabhängig von den Rollen des Erstellers, einschließlich Plattform-Admin.
 *
 * <p>Die Grenze zieht die Filterkette (Whitelist über {@code AUTH_PAT_UNBOUND}), nicht der einzelne
 * Endpunkt. Der Test prüft darum je einen Vertreter der übrigen Oberfläche und verlangt einen
 * leeren Rumpf: keine 200 mit gefilterter Liste, kein fachlicher Inhalt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class BoundTokenScopeIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final String TOKEN_HEADER = "X-Kanban-Token";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private AccessTokenRepository tokens;
  @Autowired private TokenCryptoPort crypto;

  @Test
  void boundTokenIsForbiddenOutsideItsKanbanApi() throws Exception {
    Cookie admin = session("scope-admin@example.com", PlatformRole.ADMIN);
    Cookie owner = session("scope-owner@example.com", PlatformRole.USER);
    long projectId = createProject(admin, "Scope-Projekt", "scope-owner@example.com");
    JsonNode board = createBoard(owner, projectId, "Board A");
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();
    long cardId = createCard(owner, boardId, columnId, "Karte A");
    String token = boundToken(owner, projectId, boardId, "Board-A-Token");

    expectForbiddenEverywhereOutsideKanban(token, boardId, cardId);
  }

  @Test
  void boundTokenStillReachesItsOwnBoardViaKanbanApi() throws Exception {
    Cookie admin = session("scope-own-admin@example.com", PlatformRole.ADMIN);
    Cookie owner = session("scope-own@example.com", PlatformRole.USER);
    long projectId = createProject(admin, "Eigenes Projekt", "scope-own@example.com");
    JsonNode board = createBoard(owner, projectId, "Board A");
    long boardId = board.get("id").asLong();
    String token = boundToken(owner, projectId, boardId, "Board-A-Token");

    mvc.perform(get("/api/kanban/items").header(TOKEN_HEADER, token)).andExpect(status().isOk());
    mvc.perform(get("/api/kanban/epics").header(TOKEN_HEADER, token)).andExpect(status().isOk());
    mvc.perform(
            post("/api/kanban/items")
                .header(TOKEN_HEADER, token)
                .contentType("application/json")
                .content("{\"title\":\"Via Token\",\"direct\":true}"))
        .andExpect(status().isCreated());
  }

  @Test
  void boundTokenCannotReachAnotherBoardOfTheSameCreator() throws Exception {
    Cookie admin = session("scope-two-admin@example.com", PlatformRole.ADMIN);
    Cookie owner = session("scope-two@example.com", PlatformRole.USER);
    long projectId = createProject(admin, "Zwei-Board-Projekt", "scope-two@example.com");
    JsonNode boardA = createBoard(owner, projectId, "Board A");
    JsonNode boardB = createBoard(owner, projectId, "Board B");
    long boundBoardId = boardA.get("id").asLong();
    long foreignBoardId = boardB.get("id").asLong();
    long foreignColumnId = boardB.get("columns").get(0).get("id").asLong();
    long foreignCardId = createCard(owner, foreignBoardId, foreignColumnId, "Fremde Karte");
    String token = boundToken(owner, projectId, boundBoardId, "Board-A-Token");

    // Die übrige Oberfläche ist für das gebundene Token verschlossen — auch für das eigene Board.
    mvc.perform(get("/api/boards/" + foreignBoardId).header(TOKEN_HEADER, token))
        .andExpect(status().isForbidden());
    // Innerhalb der Compat-API endet die fremde Karte am Board-Guard der card-Fassade: 404.
    mvc.perform(get("/api/kanban/items/" + foreignCardId + "/activity").header(TOKEN_HEADER, token))
        .andExpect(status().isNotFound());
  }

  @Test
  void boundTokenOfPlatformAdminIsForbiddenToo() throws Exception {
    Cookie admin = session("scope-super@example.com", PlatformRole.ADMIN);
    long projectId = createProject(admin, "Admin-Projekt", "scope-super@example.com");
    JsonNode board = createBoard(admin, projectId, "Admin-Board");
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();
    long cardId = createCard(admin, boardId, columnId, "Admin-Karte");
    String token = boundToken(admin, projectId, boardId, "Admin-Token");

    expectForbiddenEverywhereOutsideKanban(token, boardId, cardId);

    // Gegenprobe: Dasselbe Konto kommt per Session-Cookie unverändert durch.
    mvc.perform(get("/api/projects").cookie(admin)).andExpect(status().isOk());
    mvc.perform(get("/api/boards/" + boardId).cookie(admin)).andExpect(status().isOk());
    mvc.perform(get("/api/cards/" + cardId).cookie(admin)).andExpect(status().isOk());
    mvc.perform(get("/api/admin/users").cookie(admin)).andExpect(status().isOk());
  }

  @Test
  void unboundTokenKeepsItsPreviousReach() throws Exception {
    Cookie admin = session("scope-free-admin@example.com", PlatformRole.ADMIN);
    Cookie owner = session("scope-free@example.com", PlatformRole.USER);
    long projectId = createProject(admin, "Freies Projekt", "scope-free@example.com");
    JsonNode board = createBoard(owner, projectId, "Board A");
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();
    long cardId = createCard(owner, boardId, columnId, "Karte A");
    String token = unboundToken(owner, "Freies Token");

    mvc.perform(get("/api/me").header(TOKEN_HEADER, token)).andExpect(status().isOk());
    mvc.perform(get("/api/projects").header(TOKEN_HEADER, token)).andExpect(status().isOk());
    mvc.perform(get("/api/boards/" + boardId).header(TOKEN_HEADER, token))
        .andExpect(status().isOk());
    mvc.perform(get("/api/cards/" + cardId).header(TOKEN_HEADER, token)).andExpect(status().isOk());
    mvc.perform(get("/api/cards/" + cardId + "/activity").header(TOKEN_HEADER, token))
        .andExpect(status().isOk());
    // Unverändert gesperrt bleibt, was schon vorher nur per Session ging (Least Privilege).
    mvc.perform(get("/api/access-tokens").header(TOKEN_HEADER, token))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/users").header(TOKEN_HEADER, token))
        .andExpect(status().isForbidden());
  }

  @Test
  void boundTokenWrittenLikeLegacyDataIsForbiddenToo() throws Exception {
    Cookie admin = session("scope-legacy-admin@example.com", PlatformRole.ADMIN);
    Cookie owner = session("scope-legacy@example.com", PlatformRole.USER);
    long projectId = createProject(admin, "Bestandsprojekt", "scope-legacy@example.com");
    JsonNode board = createBoard(owner, projectId, "Board A");
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();
    long cardId = createCard(owner, boardId, columnId, "Karte A");
    long ownerId = users.findByEmail("scope-legacy@example.com").orElseThrow().requireId();

    // Datensatz nach dem Muster vor der Umstellung: dieselben Spalten, kein neues Feld.
    GeneratedToken generated = crypto.generate();
    tokens.save(
        new AccessToken(
            null,
            ownerId,
            projectId,
            boardId,
            "Bestandstoken",
            generated.hash(),
            "Bestandstoken",
            Instant.now(),
            null,
            false));

    expectForbiddenEverywhereOutsideKanban(generated.plaintext(), boardId, cardId);
    mvc.perform(get("/api/kanban/items").header(TOKEN_HEADER, generated.plaintext()))
        .andExpect(status().isOk());
  }

  /**
   * Je ein Vertreter der Oberfläche außerhalb von {@code /api/kanban/**}: 403 und leerer Rumpf —
   * unter MockMvc läuft der ERROR-Dispatch nicht, der Rumpf bleibt daher leer.
   */
  private void expectForbiddenEverywhereOutsideKanban(String token, long boardId, long cardId)
      throws Exception {
    List<String> paths =
        List.of(
            "/api/me",
            "/api/projects",
            "/api/boards/" + boardId,
            "/api/cards/" + cardId,
            "/api/cards/" + cardId + "/activity",
            "/api/access-tokens",
            "/api/admin/users");
    for (String path : paths) {
      mvc.perform(get(path).header(TOKEN_HEADER, token))
          .andExpect(status().isForbidden())
          .andExpect(content().string(""));
    }
  }

  private String boundToken(Cookie session, long projectId, long boardId, String name)
      throws Exception {
    return plaintext(
        session,
        "{\"name\":\"%s\",\"projectId\":%d,\"boardId\":%d}".formatted(name, projectId, boardId));
  }

  private String unboundToken(Cookie session, String name) throws Exception {
    return plaintext(session, "{\"name\":\"%s\"}".formatted(name));
  }

  private String plaintext(Cookie session, String body) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/access-tokens")
                        .cookie(session)
                        .contentType("application/json")
                        .content(body))
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
