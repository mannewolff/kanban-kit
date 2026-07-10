package org.mwolff.manban.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.application.RolePermissionRepository;
import org.mwolff.manban.project.domain.Permission;
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

/** Prüft die geseedete Rollen-Rechte-Matrix erschöpfend und einen Endpunkt-Fall. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class RbacMatrixIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String PASSWORD = "sup3r-secret";

    // Erwartete feste Matrix (Konzept §2 / F2-Seed).
    private static final Map<ProjectRole, Set<Permission>> EXPECTED = new EnumMap<>(ProjectRole.class);

    static {
        Set<Permission> reads = EnumSet.of(
                Permission.BOARD_READ, Permission.EPIC_READ, Permission.TICKET_READ,
                Permission.COMMENT_READ, Permission.ATTACHMENT_READ);

        Set<Permission> member = EnumSet.copyOf(reads);
        member.addAll(EnumSet.of(
                Permission.TICKET_CREATE, Permission.TICKET_UPDATE, Permission.TICKET_DELETE, Permission.CARD_MOVE,
                Permission.EPIC_CREATE, Permission.EPIC_UPDATE, Permission.EPIC_DELETE,
                Permission.COMMENT_CREATE, Permission.COMMENT_UPDATE,
                Permission.ATTACHMENT_CREATE, Permission.ATTACHMENT_DELETE));

        Set<Permission> admin = EnumSet.copyOf(member);
        admin.addAll(EnumSet.of(
                Permission.BOARD_CREATE, Permission.BOARD_UPDATE, Permission.BOARD_DELETE,
                Permission.COMMENT_DELETE, Permission.MEMBER_INVITE, Permission.MEMBER_REMOVE));

        EXPECTED.put(ProjectRole.VIEWER, reads);
        EXPECTED.put(ProjectRole.MEMBER, member);
        EXPECTED.put(ProjectRole.ADMIN, admin);
        EXPECTED.put(ProjectRole.OWNER, EnumSet.allOf(Permission.class));
    }

    @Autowired
    private RolePermissionRepository rolePermissions;

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

    @Test
    void eachRoleHasExactlyItsPermissions() {
        for (ProjectRole role : ProjectRole.values()) {
            for (Permission permission : Permission.values()) {
                boolean expected = EXPECTED.get(role).contains(permission);
                assertThat(rolePermissions.isGranted(role, permission))
                        .as("Rolle %s Recht %s", role, permission)
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void adminMemberCannotEditProjectButOwnerCan() throws Exception {
        Cookie alice = loginAs("owner-rbac@example.com");
        Cookie bob = loginAs("admin-rbac@example.com");
        long projectId = createProject(alice, "RBAC");

        // Bob als ADMIN — hat BOARD_CREATE, aber NICHT PROJECT_EDIT.
        memberships.save(new ProjectMembership(null, projectId, userId("admin-rbac@example.com"),
                ProjectRole.ADMIN, Instant.now()));

        mvc.perform(patch("/api/projects/" + projectId).cookie(bob)
                        .contentType("application/json").content("{\"name\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/projects/" + projectId).cookie(alice)
                        .contentType("application/json").content("{\"name\":\"OK\"}"))
                .andExpect(status().isOk());
    }

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
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }
}
