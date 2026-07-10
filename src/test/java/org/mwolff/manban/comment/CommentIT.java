package org.mwolff.manban.comment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** End-to-End-Test für Kommentar-CRUD und Editier-/Löschrechte. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class CommentIT {

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
            users.save(new AppUser(null, email, passwordEncoder.encode(PASSWORD), "Name-" + email, true, PlatformRole.USER));
        }
        return mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("manban_session");
    }

    /** Legt Projekt+Board+Karte an (alice als OWNER) und gibt die cardId zurück. */
    private long setupCard(Cookie owner, String ownerEmail) throws Exception {
        Cookie admin = platformAdminSession();
        String p = mvc.perform(post("/api/projects").cookie(admin).contentType("application/json")
                        .content("{\"name\":\"P\",\"ownerEmail\":\"%s\"}".formatted(ownerEmail)))
                .andReturn().getResponse().getContentAsString();
        long projectId = json.readTree(p).get("id").asLong();
        JsonNode board = json.readTree(mvc.perform(post("/api/projects/" + projectId + "/boards").cookie(owner)
                        .contentType("application/json").content("{\"name\":\"B\"}"))
                .andReturn().getResponse().getContentAsString());
        long boardId = board.get("id").asLong();
        long columnId = board.get("columns").get(0).get("id").asLong();
        String card = mvc.perform(post("/api/boards/" + boardId + "/cards").cookie(owner)
                        .contentType("application/json").content("{\"columnId\":%d,\"title\":\"K\"}".formatted(columnId)))
                .andReturn().getResponse().getContentAsString();
        this.projectId = projectId;
        return json.readTree(card).get("id").asLong();
    }

    private long projectId;

    private Cookie platformAdminSession() throws Exception {
        String email = "project-admin@example.com";
        if (users.findByEmail(email).isEmpty()) {
            users.save(new AppUser(null, email, passwordEncoder.encode(PASSWORD), "Person", true, PlatformRole.ADMIN));
        }
        return mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("manban_session");
    }

    private long createComment(Cookie session, long cardId, String body) throws Exception {
        String r = mvc.perform(post("/api/cards/" + cardId + "/comments").cookie(session)
                        .contentType("application/json").content("{\"body\":\"%s\"}".formatted(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(r).get("id").asLong();
    }

    @Test
    void authorCanCrudOwnComment() throws Exception {
        Cookie alice = loginAs("cm-alice@example.com");
        long cardId = setupCard(alice, "cm-alice@example.com");

        long commentId = createComment(alice, cardId, "Hallo");
        mvc.perform(get("/api/cards/" + cardId + "/comments").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").value("Hallo"))
                .andExpect(jsonPath("$[0].authorUserId").value((int) userId("cm-alice@example.com")));

        mvc.perform(patch("/api/comments/" + commentId).cookie(alice)
                        .contentType("application/json").content("{\"body\":\"Bearbeitet\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Bearbeitet"));

        mvc.perform(delete("/api/comments/" + commentId).cookie(alice)).andExpect(status().isNoContent());
    }

    @Test
    void foreignCommentCannotBeEditedByOtherMember() throws Exception {
        Cookie alice = loginAs("cm-owner@example.com");
        Cookie bob = loginAs("cm-bob@example.com");
        Cookie carol = loginAs("cm-carol@example.com");
        long cardId = setupCard(alice, "cm-owner@example.com");
        memberships.save(new ProjectMembership(null, projectId, userId("cm-bob@example.com"), ProjectRole.MEMBER, Instant.now()));
        memberships.save(new ProjectMembership(null, projectId, userId("cm-carol@example.com"), ProjectRole.MEMBER, Instant.now()));

        long bobComment = createComment(bob, cardId, "Bobs Kommentar");

        mvc.perform(patch("/api/comments/" + bobComment).cookie(carol)
                        .contentType("application/json").content("{\"body\":\"gekapert\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/comments/" + bobComment).cookie(carol))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanModerateForeignComment() throws Exception {
        Cookie alice = loginAs("cm-mod-owner@example.com");
        Cookie bob = loginAs("cm-mod-bob@example.com");
        long cardId = setupCard(alice, "cm-mod-owner@example.com");
        memberships.save(new ProjectMembership(null, projectId, userId("cm-mod-bob@example.com"), ProjectRole.MEMBER, Instant.now()));

        long bobComment = createComment(bob, cardId, "Bob");
        // OWNER darf fremden Kommentar moderieren.
        mvc.perform(patch("/api/comments/" + bobComment).cookie(alice)
                        .contentType("application/json").content("{\"body\":\"moderiert\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/comments/" + bobComment).cookie(alice))
                .andExpect(status().isNoContent());
    }

    @Test
    void viewerCannotComment() throws Exception {
        Cookie alice = loginAs("cm-v-owner@example.com");
        Cookie viewer = loginAs("cm-viewer@example.com");
        long cardId = setupCard(alice, "cm-v-owner@example.com");
        memberships.save(new ProjectMembership(null, projectId, userId("cm-viewer@example.com"), ProjectRole.VIEWER, Instant.now()));

        mvc.perform(post("/api/cards/" + cardId + "/comments").cookie(viewer)
                        .contentType("application/json").content("{\"body\":\"x\"}"))
                .andExpect(status().isForbidden());
    }
}
