package org.mwolff.manban.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** End-to-End-Test der Projekt-Verwaltung inkl. Owner-Isolation. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class ProjectIT {

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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    @Test
    void creatorBecomesOwnerAndSeesProjectInList() throws Exception {
        Cookie alice = loginAs("alice-p1@example.com");
        createProject(alice, "Alices Projekt");

        mvc.perform(get("/api/projects").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Alices Projekt')].role").value(org.hamcrest.Matchers.hasItem("OWNER")));
    }

    @Test
    void nonMemberGets404AndDoesNotSeeProject() throws Exception {
        Cookie alice = loginAs("alice-iso@example.com");
        Cookie bob = loginAs("bob-iso@example.com");
        long projectId = createProject(alice, "Geheim");

        // Bobs Liste enthält Alices Projekt nicht.
        mvc.perform(get("/api/projects").cookie(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Geheim')]").isEmpty());

        // Nichtmitglied -> 404 (kein Existenz-Leak), nicht 403.
        mvc.perform(patch("/api/projects/" + projectId).cookie(bob)
                        .contentType("application/json").content("{\"name\":\"gekapert\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/projects/" + projectId).cookie(bob))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerCanRenameAndDelete() throws Exception {
        Cookie alice = loginAs("alice-crud@example.com");
        long projectId = createProject(alice, "Alt");

        mvc.perform(patch("/api/projects/" + projectId).cookie(alice)
                        .contentType("application/json").content("{\"name\":\"Neu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Neu"));

        mvc.perform(delete("/api/projects/" + projectId).cookie(alice))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/projects").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + projectId + ")]").isEmpty());
    }

    @Test
    void memberWithoutOwnerRoleCannotModify() throws Exception {
        Cookie alice = loginAs("alice-role@example.com");
        Cookie carol = loginAs("carol-role@example.com");
        long projectId = createProject(alice, "Team");

        // Carol als MEMBER hinzufügen (Einladung kommt erst mit P3; hier direkt gesetzt).
        memberships.save(new ProjectMembership(null, projectId, userId("carol-role@example.com"),
                ProjectRole.MEMBER, Instant.now()));

        // Sieht das Projekt (Mitglied), darf es aber nicht umbenennen/löschen (nicht OWNER) -> 403.
        mvc.perform(get("/api/projects").cookie(carol))
                .andExpect(jsonPath("$[?(@.id==" + projectId + ")].role").value(org.hamcrest.Matchers.hasItem("MEMBER")));
        mvc.perform(patch("/api/projects/" + projectId).cookie(carol)
                        .contentType("application/json").content("{\"name\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/projects/" + projectId).cookie(carol))
                .andExpect(status().isForbidden());
    }
}
