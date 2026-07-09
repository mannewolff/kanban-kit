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
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** End-to-End-Test für Board- und Spalten-CRUD inkl. RBAC und Lösch-Sperre. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class BoardIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String PASSWORD = "sup3r-secret";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private ProjectMembershipRepository memberships;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    private long userId(String email) {
        return users.findByEmail(email).orElseThrow().id();
    }

    private Cookie loginAs(String email) throws Exception {
        if (users.findByEmail(email).isEmpty()) {
            users.save(new AppUser(null, email, passwordEncoder.encode(PASSWORD), "Person", true, PlatformRole.USER));
        }
        return mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("manban_session");
    }

    private long createProject(Cookie session, String name) throws Exception {
        String body = mvc.perform(post("/api/projects").cookie(session)
                        .contentType("application/json").content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private JsonNode createBoard(Cookie session, long projectId, String name) throws Exception {
        String body = mvc.perform(post("/api/projects/" + projectId + "/boards").cookie(session)
                        .contentType("application/json").content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    void boardCreatedWithDefaultColumns() throws Exception {
        Cookie alice = loginAs("board-owner@example.com");
        long projectId = createProject(alice, "P");

        mvc.perform(post("/api/projects/" + projectId + "/boards").cookie(alice)
                        .contentType("application/json").content("{\"name\":\"Board 1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Board 1"))
                .andExpect(jsonPath("$.columns.length()").value(4))
                .andExpect(jsonPath("$.columns[0].name").value("Backlog"))
                .andExpect(jsonPath("$.columns[3].name").value("Done"))
                .andExpect(jsonPath("$.columns[0].position").value(0));
    }

    @Test
    void nonMemberCannotCreateBoard() throws Exception {
        Cookie alice = loginAs("bo-owner@example.com");
        Cookie eve = loginAs("bo-eve@example.com");
        long projectId = createProject(alice, "Closed");

        mvc.perform(post("/api/projects/" + projectId + "/boards").cookie(eve)
                        .contentType("application/json").content("{\"name\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void viewerCannotEditColumns() throws Exception {
        Cookie alice = loginAs("col-owner@example.com");
        Cookie viewer = loginAs("col-viewer@example.com");
        long projectId = createProject(alice, "RBAC");
        long boardId = createBoard(alice, projectId, "B").get("id").asLong();
        memberships.save(new ProjectMembership(null, projectId, userId("col-viewer@example.com"),
                ProjectRole.VIEWER, Instant.now()));

        mvc.perform(post("/api/boards/" + boardId + "/columns").cookie(viewer)
                        .contentType("application/json").content("{\"name\":\"Neu\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void columnLifecycleAddUpdateReorderDelete() throws Exception {
        Cookie alice = loginAs("cl-owner@example.com");
        long projectId = createProject(alice, "CL");
        JsonNode board = createBoard(alice, projectId, "B");
        long boardId = board.get("id").asLong();

        // Neue Spalte anlegen (mit WIP-Limit)
        String added = mvc.perform(post("/api/boards/" + boardId + "/columns").cookie(alice)
                        .contentType("application/json").content("{\"name\":\"Extra\",\"wipLimit\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.wipLimit").value(3))
                .andReturn().getResponse().getContentAsString();
        long extraId = json.readTree(added).get("id").asLong();

        // Umbenennen
        mvc.perform(patch("/api/columns/" + extraId).cookie(alice)
                        .contentType("application/json").content("{\"name\":\"Umbenannt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Umbenannt"));

        // Umsortieren: die 5 Spalten in umgekehrter Reihenfolge
        long c0 = board.get("columns").get(0).get("id").asLong();
        long c1 = board.get("columns").get(1).get("id").asLong();
        long c2 = board.get("columns").get(2).get("id").asLong();
        long c3 = board.get("columns").get(3).get("id").asLong();
        mvc.perform(put("/api/boards/" + boardId + "/columns/order").cookie(alice)
                        .contentType("application/json")
                        .content("{\"columnIds\":[%d,%d,%d,%d,%d]}".formatted(extraId, c3, c2, c1, c0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value((int) extraId))
                .andExpect(jsonPath("$[0].position").value(0))
                .andExpect(jsonPath("$[4].id").value((int) c0));

        // Leere Spalte löschen
        mvc.perform(delete("/api/columns/" + extraId).cookie(alice))
                .andExpect(status().isNoContent());
    }

    @Test
    void getBoardReturnsColumns() throws Exception {
        Cookie alice = loginAs("get-board@example.com");
        long projectId = createProject(alice, "GB");
        long boardId = createBoard(alice, projectId, "Board").get("id").asLong();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/boards/" + boardId).cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Board"))
                .andExpect(jsonPath("$.columns.length()").value(4));
    }

    @Test
    void deleteColumnWithCardsIsBlocked() throws Exception {
        Cookie alice = loginAs("cards-owner@example.com");
        long projectId = createProject(alice, "Cards");
        JsonNode board = createBoard(alice, projectId, "B");
        long boardId = board.get("id").asLong();
        long columnId = board.get("columns").get(0).get("id").asLong();

        // Karte direkt einfügen (Karten-CRUD kommt erst mit B2).
        jdbc.update("INSERT INTO card (board_id, column_id, number, title, position_in_column) VALUES (?,?,?,?,?)",
                boardId, columnId, 1, "Testkarte", 0);

        mvc.perform(delete("/api/columns/" + columnId).cookie(alice))
                .andExpect(status().isConflict());

        // Nach Entfernen der Karte klappt das Löschen.
        jdbc.update("DELETE FROM card WHERE column_id = ?", columnId);
        mvc.perform(delete("/api/columns/" + columnId).cookie(alice))
                .andExpect(status().isNoContent());
    }
}
