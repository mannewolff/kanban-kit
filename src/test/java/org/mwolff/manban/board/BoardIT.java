package org.mwolff.manban.board;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-End-Test für Board- und Spalten-CRUD inkl. RBAC und Lösch-Sperre. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class BoardIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().id();
  }

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

  private JsonNode createBoard(Cookie session, long projectId, String name) throws Exception {
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
    return json.readTree(body);
  }

  @Test
  void boardCreatedWithDefaultColumns() throws Exception {
    Cookie alice = loginAs("board-owner@example.com");
    long projectId = createProject("board-owner@example.com", "P");

    mvc.perform(
            post("/api/projects/" + projectId + "/boards")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"name\":\"Board 1\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Board 1"))
        .andExpect(jsonPath("$.columns.length()").value(5))
        .andExpect(jsonPath("$.columns[0].name").value("Backlog"))
        .andExpect(jsonPath("$.columns[1].name").value("Ready"))
        .andExpect(jsonPath("$.columns[2].name").value("In Progress"))
        .andExpect(jsonPath("$.columns[3].name").value("In Review"))
        .andExpect(jsonPath("$.columns[4].name").value("Done"))
        .andExpect(jsonPath("$.columns[0].position").value(0));
  }

  @Test
  void nonMemberCannotCreateBoard() throws Exception {
    loginAs("bo-owner@example.com");
    Cookie eve = loginAs("bo-eve@example.com");
    long projectId = createProject("bo-owner@example.com", "Closed");

    mvc.perform(
            post("/api/projects/" + projectId + "/boards")
                .cookie(eve)
                .contentType("application/json")
                .content("{\"name\":\"X\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void viewerCannotEditColumns() throws Exception {
    Cookie alice = loginAs("col-owner@example.com");
    Cookie viewer = loginAs("col-viewer@example.com");
    long projectId = createProject("col-owner@example.com", "RBAC");
    long boardId = createBoard(alice, projectId, "B").get("id").asLong();
    memberships.save(
        new ProjectMembership(
            null, projectId, userId("col-viewer@example.com"), ProjectRole.VIEWER, Instant.now()));

    mvc.perform(
            post("/api/boards/" + boardId + "/columns")
                .cookie(viewer)
                .contentType("application/json")
                .content("{\"name\":\"Neu\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void columnLifecycleAddUpdateReorderDelete() throws Exception {
    Cookie alice = loginAs("cl-owner@example.com");
    long projectId = createProject("cl-owner@example.com", "CL");
    JsonNode board = createBoard(alice, projectId, "B");
    long boardId = board.get("id").asLong();

    // Neue Spalte anlegen (mit WIP-Limit)
    String added =
        mvc.perform(
                post("/api/boards/" + boardId + "/columns")
                    .cookie(alice)
                    .contentType("application/json")
                    .content("{\"name\":\"Extra\",\"wipLimit\":3}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.wipLimit").value(3))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long extraId = json.readTree(added).get("id").asLong();

    // Umbenennen
    mvc.perform(
            patch("/api/columns/" + extraId)
                .cookie(alice)
                .contentType("application/json")
                .content("{\"name\":\"Umbenannt\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Umbenannt"));

    // Umsortieren: alle Spalten (5 Default + Extra) in umgekehrter Reihenfolge
    long c0 = board.get("columns").get(0).get("id").asLong();
    long c1 = board.get("columns").get(1).get("id").asLong();
    long c2 = board.get("columns").get(2).get("id").asLong();
    long c3 = board.get("columns").get(3).get("id").asLong();
    long c4 = board.get("columns").get(4).get("id").asLong();
    mvc.perform(
            put("/api/boards/" + boardId + "/columns/order")
                .cookie(alice)
                .contentType("application/json")
                .content(
                    "{\"columnIds\":[%d,%d,%d,%d,%d,%d]}".formatted(extraId, c4, c3, c2, c1, c0)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value((int) extraId))
        .andExpect(jsonPath("$[0].position").value(0))
        .andExpect(jsonPath("$[5].id").value((int) c0));

    // Leere Spalte löschen
    mvc.perform(delete("/api/columns/" + extraId).cookie(alice)).andExpect(status().isNoContent());
  }

  @Test
  void getBoardReturnsColumns() throws Exception {
    Cookie alice = loginAs("get-board@example.com");
    long projectId = createProject("get-board@example.com", "GB");
    long boardId = createBoard(alice, projectId, "Board").get("id").asLong();

    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/boards/" + boardId)
                .cookie(alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Board"))
        .andExpect(jsonPath("$.columns.length()").value(5));
  }

  private int cardCount(long boardId) {
    Integer count =
        jdbc.queryForObject("SELECT count(*) FROM card WHERE board_id = ?", Integer.class, boardId);
    return count == null ? 0 : count;
  }

  @Test
  void boardArchiveRestorePurgeLifecycle() throws Exception {
    Cookie alice = loginAs("arch-owner@example.com");
    long projectId = createProject("arch-owner@example.com", "Arch");
    JsonNode board = createBoard(alice, projectId, "Lifecycle");
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();
    jdbc.update(
        "INSERT INTO card (board_id, column_id, number, title, position_in_column) "
            + "VALUES (?,?,?,?,?)",
        boardId,
        columnId,
        1,
        "Karte",
        0);

    // Archivieren (Löschen wird zum Archivieren): Karte bleibt erhalten.
    mvc.perform(delete("/api/boards/" + boardId).cookie(alice)).andExpect(status().isNoContent());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/boards/" + boardId)
                .cookie(alice))
        .andExpect(status().isNotFound());
    org.assertj.core.api.Assertions.assertThat(cardCount(boardId)).isEqualTo(1);

    // Archiviertes Board taucht nur in der Archiv-Liste auf, nicht in der aktiven.
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/projects/" + projectId + "/boards")
                .cookie(alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/projects/" + projectId + "/boards/archived")
                .cookie(alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value((int) boardId));

    // Wiederherstellen: wieder aktiv und auffindbar.
    mvc.perform(post("/api/boards/" + boardId + "/restore").cookie(alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Lifecycle"));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/boards/" + boardId)
                .cookie(alice))
        .andExpect(status().isOk());

    // Endgültiges Löschen erst nach erneutem Archivieren; Cascade entfernt die Karte.
    mvc.perform(delete("/api/boards/" + boardId + "/purge").cookie(alice))
        .andExpect(status().isConflict());
    mvc.perform(delete("/api/boards/" + boardId).cookie(alice)).andExpect(status().isNoContent());
    mvc.perform(delete("/api/boards/" + boardId + "/purge").cookie(alice))
        .andExpect(status().isNoContent());
    org.assertj.core.api.Assertions.assertThat(cardCount(boardId)).isZero();
  }

  @Test
  void viewerCannotArchiveBoard() throws Exception {
    Cookie alice = loginAs("arch-rbac-owner@example.com");
    Cookie viewer = loginAs("arch-rbac-viewer@example.com");
    long projectId = createProject("arch-rbac-owner@example.com", "ArchRBAC");
    long boardId = createBoard(alice, projectId, "B").get("id").asLong();
    memberships.save(
        new ProjectMembership(
            null,
            projectId,
            userId("arch-rbac-viewer@example.com"),
            ProjectRole.VIEWER,
            Instant.now()));

    mvc.perform(delete("/api/boards/" + boardId).cookie(viewer)).andExpect(status().isForbidden());
  }

  @Test
  void deleteColumnWithCardsIsBlocked() throws Exception {
    Cookie alice = loginAs("cards-owner@example.com");
    long projectId = createProject("cards-owner@example.com", "Cards");
    JsonNode board = createBoard(alice, projectId, "B");
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();

    // Karte direkt einfügen (Karten-CRUD kommt erst mit B2).
    jdbc.update(
        "INSERT INTO card (board_id, column_id, number, title, position_in_column) "
            + "VALUES (?,?,?,?,?)",
        boardId,
        columnId,
        1,
        "Testkarte",
        0);

    mvc.perform(delete("/api/columns/" + columnId).cookie(alice)).andExpect(status().isConflict());

    // Nach Entfernen der Karte klappt das Löschen.
    jdbc.update("DELETE FROM card WHERE column_id = ?", columnId);
    mvc.perform(delete("/api/columns/" + columnId).cookie(alice)).andExpect(status().isNoContent());
  }
}
