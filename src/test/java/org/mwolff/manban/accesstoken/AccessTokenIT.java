package org.mwolff.manban.accesstoken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-End-Test der PAT-Funktionalität (Erstellen, Nutzen, Widerrufen, Least Privilege). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AccessTokenIT extends AbstractIntegrationTest {

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

  @Test
  void createUseListRevokeFlow() throws Exception {
    Cookie session = loginAs("pat-user@example.com");

    // 1. Anlegen (Cookie) -> Klartext einmalig
    String body =
        mvc.perform(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"CI-Runner\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.plaintext").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode created = json.readTree(body);
    String plaintext = created.get("plaintext").asText();
    long tokenId = created.get("id").asLong();
    assertThat(plaintext).startsWith("tk_");

    // 2. Mit PAT authentifizieren
    mvc.perform(get("/api/me").header("X-Kanban-Token", plaintext))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("pat-user@example.com"));

    // 3. Auflisten (Cookie) — kein Klartext/Hash
    mvc.perform(get("/api/access-tokens").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("CI-Runner"))
        .andExpect(jsonPath("$[0].revoked").value(false));

    // 4. Widerrufen (Cookie) -> PAT danach 401
    mvc.perform(delete("/api/access-tokens/" + tokenId).cookie(session))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/me").header("X-Kanban-Token", plaintext))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unknownPatIsUnauthorized() throws Exception {
    mvc.perform(get("/api/me").header("X-Kanban-Token", "tk_deadbeef"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void tokenManagementViaPatIsForbidden() throws Exception {
    Cookie session = loginAs("pat-admin@example.com");
    String body =
        mvc.perform(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"self\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String plaintext = json.readTree(body).get("plaintext").asText();

    // Verwaltung per PAT (ohne Cookie) muss verweigert werden (Least Privilege).
    mvc.perform(
            post("/api/access-tokens")
                .header("X-Kanban-Token", plaintext)
                .contentType("application/json")
                .content("{\"name\":\"escalate\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/access-tokens").header("X-Kanban-Token", plaintext))
        .andExpect(status().isForbidden());
  }

  // --- Projekt-/Board-Bindung (#44) -----------------------------------------

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

  private long createBoard(Cookie session, long projectId, String name) throws Exception {
    String body =
        mvc.perform(
                post("/api/projects/" + projectId + "/boards")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\"}".formatted(name)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  @Test
  void boundTokenAuthenticatesAndPersistsBinding() throws Exception {
    Cookie session = loginAs("bind-owner@example.com");
    long projectId = createProject("bind-owner@example.com", "Bindungsprojekt");
    long boardId = createBoard(session, projectId, "Board A");

    String body =
        mvc.perform(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType("application/json")
                    .content(
                        "{\"name\":\"Board-A-Token\",\"projectId\":%d,\"boardId\":%d}"
                            .formatted(projectId, boardId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String plaintext = json.readTree(body).get("plaintext").asText();

    // PAT authentifiziert weiterhin (Filter löst über resolveBinding auf).
    mvc.perform(get("/api/me").header("X-Kanban-Token", plaintext)).andExpect(status().isOk());

    // Liste (neuestes Token zuerst) zeigt die persistierte Bindung.
    mvc.perform(get("/api/access-tokens").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Board-A-Token"))
        .andExpect(jsonPath("$[0].projectId").value((int) projectId))
        .andExpect(jsonPath("$[0].boardId").value((int) boardId));
  }

  @Test
  void bindingToBoardWithoutMembershipIsForbidden() throws Exception {
    Cookie owner = loginAs("bind-a@example.com");
    long projectId = createProject("bind-a@example.com", "A-Projekt");
    long boardId = createBoard(owner, projectId, "A-Board");

    Cookie outsider = loginAs("bind-b@example.com");
    mvc.perform(
            post("/api/access-tokens")
                .cookie(outsider)
                .contentType("application/json")
                .content(
                    "{\"name\":\"steal\",\"projectId\":%d,\"boardId\":%d}"
                        .formatted(projectId, boardId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void bindingToBoardOfDifferentProjectIsBadRequest() throws Exception {
    Cookie session = loginAs("bind-cross@example.com");
    long p1 = createProject("bind-cross@example.com", "P1");
    long p2 = createProject("bind-cross@example.com", "P2");
    long b1 = createBoard(session, p1, "B1");

    mvc.perform(
            post("/api/access-tokens")
                .cookie(session)
                .contentType("application/json")
                .content("{\"name\":\"cross\",\"projectId\":%d,\"boardId\":%d}".formatted(p2, b1)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void bindingWithOnlyProjectIsBadRequest() throws Exception {
    Cookie session = loginAs("bind-partial@example.com");
    long p1 = createProject("bind-partial@example.com", "PP");

    mvc.perform(
            post("/api/access-tokens")
                .cookie(session)
                .contentType("application/json")
                .content("{\"name\":\"partial\",\"projectId\":%d}".formatted(p1)))
        .andExpect(status().isBadRequest());
  }
}
