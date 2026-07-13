package org.mwolff.manban.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/**
 * Prüft die Admin-Nutzerverwaltung: Liste, Rollenwechsel, 403 für Nicht-Admin, Last-Admin-Guard.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AdminUserIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;

  private long ensureUser(String email, PlatformRole role) {
    return users
        .findByEmail(email)
        .orElseGet(
            () ->
                users.save(
                    new AppUser(
                        null, email, passwordEncoder.encode(PASSWORD), "Person", true, role)))
        .id();
  }

  /** Verifizierter, aber noch nicht freigegebener Benutzer ({@code approvedAt=null}). */
  private long ensurePendingUser(String email) {
    return users
        .findByEmail(email)
        .orElseGet(
            () ->
                users.save(
                    new AppUser(
                        null,
                        email,
                        passwordEncoder.encode(PASSWORD),
                        "Person",
                        true,
                        PlatformRole.USER,
                        null,
                        null)))
        .id();
  }

  private org.springframework.test.web.servlet.ResultActions attemptLogin(String email)
      throws Exception {
    return mvc.perform(
        post("/api/auth/login")
            .contentType("application/json")
            .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)));
  }

  private Cookie login(String email, PlatformRole role) throws Exception {
    ensureUser(email, role);
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
  void adminListsAndPromotesUsersNonAdminForbidden() throws Exception {
    Cookie admin = login("au-admin@example.com", PlatformRole.ADMIN);
    long targetId = ensureUser("au-target@example.com", PlatformRole.USER);

    // Admin listet Nutzer.
    mvc.perform(get("/api/admin/users").cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.email=='au-target@example.com')].platformRole").value("USER"));

    // Admin macht den Nutzer zum Admin.
    mvc.perform(
            patch("/api/admin/users/" + targetId)
                .cookie(admin)
                .contentType("application/json")
                .content("{\"platformRole\":\"ADMIN\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.platformRole").value("ADMIN"));

    // Nicht-Admin bekommt 403.
    Cookie user = login("au-user@example.com", PlatformRole.USER);
    mvc.perform(get("/api/admin/users").cookie(user)).andExpect(status().isForbidden());
  }

  @Test
  void pendingUserCannotLoginUntilAdminApprovesNonAdminForbidden() throws Exception {
    Cookie admin = login("ap-admin@example.com", PlatformRole.ADMIN);
    long pendingId = ensurePendingUser("ap-pending@example.com");

    // Nicht freigegebener Nutzer: Login abgelehnt (403).
    attemptLogin("ap-pending@example.com").andExpect(status().isForbidden());

    // Nicht-Admin darf nicht freigeben (403).
    Cookie plain = login("ap-user@example.com", PlatformRole.USER);
    mvc.perform(post("/api/admin/users/" + pendingId + "/approve").cookie(plain))
        .andExpect(status().isForbidden());

    // Admin gibt frei.
    mvc.perform(post("/api/admin/users/" + pendingId + "/approve").cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvedAt").isNotEmpty());

    // Jetzt gelingt der Login.
    attemptLogin("ap-pending@example.com").andExpect(status().isOk());
  }

  @Test
  void lastAdminCannotBeDemoted() throws Exception {
    // Ausgangslage deterministisch herstellen: genau ein Admin (Admin-Zahl ist global).
    ensureUser("last-admin@example.com", PlatformRole.ADMIN);
    users.findAll().stream()
        .filter(
            u ->
                u.platformRole() == PlatformRole.ADMIN
                    && !"last-admin@example.com".equals(u.email()))
        .forEach(u -> users.save(u.withPlatformRole(PlatformRole.USER)));
    AppUser la = users.findByEmail("last-admin@example.com").orElseThrow();
    if (la.platformRole() != PlatformRole.ADMIN) {
      users.save(la.withPlatformRole(PlatformRole.ADMIN));
    }

    Cookie admin = login("last-admin@example.com", PlatformRole.ADMIN);
    long adminId = users.findByEmail("last-admin@example.com").orElseThrow().id();

    // Als einziger Admin schlägt die Selbst-Degradierung mit 409 fehl.
    mvc.perform(
            patch("/api/admin/users/" + adminId)
                .cookie(admin)
                .contentType("application/json")
                .content("{\"platformRole\":\"USER\"}"))
        .andExpect(status().isConflict());
  }
}
