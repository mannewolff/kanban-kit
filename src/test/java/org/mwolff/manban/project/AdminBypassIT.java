package org.mwolff.manban.project;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** Prüft, dass ein Plattform-Admin Super-User ist: Zugriff auf fremde Projekte + Gesamt-Listing. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AdminBypassIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  private Cookie login(String email, PlatformRole role) throws Exception {
    if (users.findByEmail(email).isEmpty()) {
      users.save(new AppUser(null, email, passwordEncoder.encode(PASSWORD), "Person", true, role));
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

  @Test
  void adminActsOnForeignProjectButNonMemberCannot() throws Exception {
    Cookie alice = login("bypass-alice@example.com", PlatformRole.USER);
    long projectId = createProject("bypass-alice@example.com", "Alices Projekt");
    JsonNode board = createBoard(alice, projectId);
    long boardId = board.get("id").asLong();
    long backlog = board.get("columns").get(0).get("id").asLong();

    Cookie admin = login("bypass-admin@example.com", PlatformRole.ADMIN);
    Cookie eve = login("bypass-eve@example.com", PlatformRole.USER);

    // Admin (kein Mitglied) darf eine Karte anlegen.
    mvc.perform(
            post("/api/boards/" + boardId + "/cards")
                .cookie(admin)
                .contentType("application/json")
                .content("{\"columnId\":%d,\"title\":\"Admin-Karte\"}".formatted(backlog)))
        .andExpect(status().isCreated());

    // Normaler Nicht-Member bekommt 404 (kein Existenz-Leak).
    mvc.perform(
            post("/api/boards/" + boardId + "/cards")
                .cookie(eve)
                .contentType("application/json")
                .content("{\"columnId\":%d,\"title\":\"Eve-Karte\"}".formatted(backlog)))
        .andExpect(status().isNotFound());
  }

  /**
   * Der Plattform-Admin liest die Mitgliederliste eines fremden Projekts über die vollständige
   * Session-Kette — ohne selbst je Mitglied zu werden (Issue #582).
   */
  @Test
  void adminReadsMemberListOfForeignProjectWithoutOwnMembership() throws Exception {
    login("members-alice@example.com", PlatformRole.USER);
    long projectId = createProject("members-alice@example.com", "Alices Mitglieder");
    long aliceId = users.findByEmail("members-alice@example.com").orElseThrow().requireId();

    Cookie admin = login("members-admin@example.com", PlatformRole.ADMIN);
    long adminId = users.findByEmail("members-admin@example.com").orElseThrow().requireId();
    assertThat(memberships.findByProjectIdAndUserId(projectId, adminId)).isEmpty();

    mvc.perform(get("/api/projects/" + projectId + "/members").cookie(admin))
        .andExpect(status().isOk())
        // Genau das persistierte Owner-Mitglied — der Admin selbst erscheint nicht.
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].userId").value(aliceId))
        .andExpect(jsonPath("$[0].email").value("members-alice@example.com"))
        .andExpect(jsonPath("$[0].role").value("OWNER"));

    // Der Lesezugriff hat keine Mitgliedschaft angelegt.
    assertThat(memberships.findByProjectIdAndUserId(projectId, adminId)).isEmpty();
  }

  /** Der Admin-Bypass gilt nur für bestehende Projekte: unbekannte ID bleibt 404, nie 200 []. */
  @Test
  void adminGetsNotFoundForMemberListOfUnknownProject() throws Exception {
    Cookie admin = login("unknown-admin@example.com", PlatformRole.ADMIN);

    mvc.perform(get("/api/projects/999999/members").cookie(admin)).andExpect(status().isNotFound());
  }

  @Test
  void adminSeesAllProjectsUserSeesOnlyOwn() throws Exception {
    Cookie alice = login("all-alice@example.com", PlatformRole.USER);
    login("all-bob@example.com", PlatformRole.USER);
    createProject("all-alice@example.com", "Alice P");
    createProject("all-bob@example.com", "Bob P");

    Cookie admin = login("all-admin@example.com", PlatformRole.ADMIN);
    String adminList =
        mvc.perform(get("/api/projects").cookie(admin))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // Admin sieht mindestens beide Projekte.
    org.assertj.core.api.Assertions.assertThat(adminList).contains("Alice P").contains("Bob P");

    // Alice sieht nur ihr eigenes.
    mvc.perform(get("/api/projects").cookie(alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Alice P"));
  }
}
