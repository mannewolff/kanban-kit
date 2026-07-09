package org.mwolff.manban.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** Prüft die Admin-Nutzerverwaltung: Liste, Rollenwechsel, 403 für Nicht-Admin, Last-Admin-Guard. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class AdminUserIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String PASSWORD = "sup3r-secret";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private long ensureUser(String email, PlatformRole role) {
        return users.findByEmail(email)
                .orElseGet(() -> users.save(
                        new AppUser(null, email, passwordEncoder.encode(PASSWORD), "Person", true, role)))
                .id();
    }

    private Cookie login(String email, PlatformRole role) throws Exception {
        ensureUser(email, role);
        return mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("manban_session");
    }

    @Test
    void adminListsAndPromotesUsersNonAdminForbidden() throws Exception {
        Cookie admin = login("au-admin@example.com", PlatformRole.ADMIN);
        long targetId = ensureUser("au-target@example.com", PlatformRole.USER);

        // Admin listet Nutzer.
        mvc.perform(get("/api/admin/users").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='au-target@example.com')].platformRole").value("USER"));

        // Admin macht den Nutzer zum Admin.
        mvc.perform(patch("/api/admin/users/" + targetId).cookie(admin)
                        .contentType("application/json").content("{\"platformRole\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformRole").value("ADMIN"));

        // Nicht-Admin bekommt 403.
        Cookie user = login("au-user@example.com", PlatformRole.USER);
        mvc.perform(get("/api/admin/users").cookie(user)).andExpect(status().isForbidden());
    }

    @Test
    void lastAdminCannotBeDemoted() throws Exception {
        // Ausgangslage deterministisch herstellen: genau ein Admin (Admin-Zahl ist global).
        ensureUser("last-admin@example.com", PlatformRole.ADMIN);
        users.findAll().stream()
                .filter(u -> u.platformRole() == PlatformRole.ADMIN && !u.email().equals("last-admin@example.com"))
                .forEach(u -> users.save(u.withPlatformRole(PlatformRole.USER)));
        AppUser la = users.findByEmail("last-admin@example.com").orElseThrow();
        if (la.platformRole() != PlatformRole.ADMIN) {
            users.save(la.withPlatformRole(PlatformRole.ADMIN));
        }

        Cookie admin = login("last-admin@example.com", PlatformRole.ADMIN);
        long adminId = users.findByEmail("last-admin@example.com").orElseThrow().id();

        // Als einziger Admin schlägt die Selbst-Degradierung mit 409 fehl.
        mvc.perform(patch("/api/admin/users/" + adminId).cookie(admin)
                        .contentType("application/json").content("{\"platformRole\":\"USER\"}"))
                .andExpect(status().isConflict());
    }
}
