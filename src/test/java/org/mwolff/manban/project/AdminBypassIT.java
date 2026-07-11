package org.mwolff.manban.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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

/** Prüft, dass ein Plattform-Admin Super-User ist: Zugriff auf fremde Projekte + Gesamt-Listing. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class AdminBypassIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
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

  @Test
  void adminSeesAllProjectsUserSeesOnlyOwn() throws Exception {
    Cookie alice = login("all-alice@example.com", PlatformRole.USER);
    Cookie bob = login("all-bob@example.com", PlatformRole.USER);
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
