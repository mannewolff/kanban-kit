package org.mwolff.manban.card;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** End-to-End-Test für Karten-CRUD, board-scoped Nummern, Abhängigkeiten und Archiv-Flow. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class CardIT {

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

    private JsonNode createBoard(Cookie session, long projectId) throws Exception {
        String body = mvc.perform(post("/api/projects/" + projectId + "/boards").cookie(session)
                        .contentType("application/json").content("{\"name\":\"B\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode createCard(Cookie session, long boardId, long columnId, String title, String depsJson)
            throws Exception {
        String deps = depsJson == null ? "" : ",\"dependencies\":" + depsJson;
        String body = mvc.perform(post("/api/boards/" + boardId + "/cards").cookie(session)
                        .contentType("application/json")
                        .content("{\"columnId\":%d,\"title\":\"%s\"%s}".formatted(columnId, title, deps)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    void cardNumbersAreSequentialAndBoardScoped() throws Exception {
        Cookie alice = loginAs("card-owner@example.com");
        long projectId = createProject(alice, "P");
        JsonNode board = createBoard(alice, projectId);
        long boardId = board.get("id").asLong();
        long columnId = board.get("columns").get(0).get("id").asLong();

        int n1 = createCard(alice, boardId, columnId, "A", null).get("number").asInt();
        int n2 = createCard(alice, boardId, columnId, "B", null).get("number").asInt();
        int n3 = createCard(alice, boardId, columnId, "C", null).get("number").asInt();
        org.assertj.core.api.Assertions.assertThat(new int[] {n1, n2, n3}).containsExactly(1, 2, 3);

        // Zweites Board startet wieder bei 1 (board-scoped).
        JsonNode board2 = createBoard(alice, projectId);
        long boardId2 = board2.get("id").asLong();
        long columnId2 = board2.get("columns").get(0).get("id").asLong();
        org.assertj.core.api.Assertions.assertThat(
                createCard(alice, boardId2, columnId2, "X", null).get("number").asInt()).isEqualTo(1);
    }

    @Test
    void dependencyValidation() throws Exception {
        Cookie alice = loginAs("dep-owner@example.com");
        long projectId = createProject(alice, "Dep");
        JsonNode board = createBoard(alice, projectId);
        long boardId = board.get("id").asLong();
        long columnId = board.get("columns").get(0).get("id").asLong();

        createCard(alice, boardId, columnId, "First", null); // number 1
        JsonNode second = createCard(alice, boardId, columnId, "Second", "[1]"); // hängt von #1 ab
        org.assertj.core.api.Assertions.assertThat(second.get("dependencies").get(0).asInt()).isEqualTo(1);
        long secondId = second.get("id").asLong();
        int secondNumber = second.get("number").asInt();

        // Unbekannte Nummer -> 400
        mvc.perform(post("/api/boards/" + boardId + "/cards").cookie(alice).contentType("application/json")
                        .content("{\"columnId\":%d,\"title\":\"Z\",\"dependencies\":[999]}".formatted(columnId)))
                .andExpect(status().isBadRequest());

        // Selbstreferenz -> 400
        mvc.perform(patch("/api/cards/" + secondId).cookie(alice).contentType("application/json")
                        .content("{\"title\":\"Second\",\"dependencies\":[%d]}".formatted(secondNumber)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void archiveRestoreFlow() throws Exception {
        Cookie alice = loginAs("arch-owner@example.com");
        long projectId = createProject(alice, "Arch");
        JsonNode board = createBoard(alice, projectId);
        long boardId = board.get("id").asLong();
        long columnId = board.get("columns").get(0).get("id").asLong();
        long cardId = createCard(alice, boardId, columnId, "Karte", null).get("id").asLong();

        mvc.perform(post("/api/cards/" + cardId + "/archive").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));

        // Eine neue Karte in derselben Spalte kollidiert nicht (archivierte liegt außerhalb des Namespace).
        createCard(alice, boardId, columnId, "Nachrücker", null);

        mvc.perform(post("/api/cards/" + cardId + "/restore").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void updateAndDelete() throws Exception {
        Cookie alice = loginAs("ud-owner@example.com");
        long projectId = createProject(alice, "UD");
        JsonNode board = createBoard(alice, projectId);
        long boardId = board.get("id").asLong();
        long columnId = board.get("columns").get(0).get("id").asLong();
        long cardId = createCard(alice, boardId, columnId, "Alt", null).get("id").asLong();

        mvc.perform(patch("/api/cards/" + cardId).cookie(alice).contentType("application/json")
                        .content("{\"title\":\"Neu\",\"description\":\"**md**\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Neu"))
                .andExpect(jsonPath("$.description").value("**md**"));

        mvc.perform(delete("/api/cards/" + cardId).cookie(alice)).andExpect(status().isNoContent());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/boards/" + boardId + "/cards").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void viewerCannotCreateCard() throws Exception {
        Cookie alice = loginAs("cc-owner@example.com");
        Cookie viewer = loginAs("cc-viewer@example.com");
        long projectId = createProject(alice, "V");
        JsonNode board = createBoard(alice, projectId);
        long boardId = board.get("id").asLong();
        long columnId = board.get("columns").get(0).get("id").asLong();
        memberships.save(new ProjectMembership(null, projectId, userId("cc-viewer@example.com"),
                ProjectRole.VIEWER, Instant.now()));

        mvc.perform(post("/api/boards/" + boardId + "/cards").cookie(viewer).contentType("application/json")
                        .content("{\"columnId\":%d,\"title\":\"X\"}".formatted(columnId)))
                .andExpect(status().isForbidden());
    }
}
