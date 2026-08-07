package org.mwolff.manban.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * End-to-End: Ein Plattform-Admin ohne Freigabe-Zeitstempel kann nicht nur anmelden (Issue #556),
 * sondern auch arbeiten — Projekt anlegen und in ein Projekt aufgenommen werden.
 *
 * <p>Dieser Zustand entsteht, wenn die Rolle per rohem {@code UPDATE} gesetzt wurde, das jeden
 * Service umgeht. Vor dieser Änderung meldete sich ein solcher Admin erfolgreich an und lief beim
 * ersten Projekt erneut in die Freigabe-Sperre, diesmal als HTTP 422 ohne Ausweg. Die Gegenproben
 * sichern, dass ein wartender Nutzer mit Rolle USER weiterhin abgewiesen wird.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PendingPlatformAdminIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  /** Legt einen Benutzer mit der gewünschten Rolle und ohne Freigabe-Zeitstempel an. */
  private void createPending(String email, PlatformRole role) {
    users.save(
        new AppUser(
            null, email, passwordEncoder.encode(PASSWORD), "Person", true, role, null, null));
  }

  private void createApprovedAdmin(String email) {
    users.save(
        new AppUser(
            null, email, passwordEncoder.encode(PASSWORD), "Person", true, PlatformRole.ADMIN));
  }

  private Cookie loginAs(String email) throws Exception {
    return mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("manban_session");
  }

  private long createProjectAs(Cookie session, String name, String ownerEmail) throws Exception {
    String body =
        mvc.perform(
                post("/api/projects")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\",\"ownerEmail\":\"%s\"}".formatted(name, ownerEmail)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  @Test
  void pendingPlatformAdminCanCreateProjectForHimself() throws Exception {
    // Given: genau der Kundenfall — Rolle per SQL gesetzt, approved_at blieb NULL.
    String admin = "pending-admin-owner@example.com";
    createPending(admin, PlatformRole.ADMIN);
    Cookie session = loginAs(admin);

    // When / Then: anmelden gelingt (Issue #556) und das erste Projekt entsteht.
    mvc.perform(
            post("/api/projects")
                .cookie(session)
                .contentType("application/json")
                .content("{\"name\":\"Erstes Projekt\",\"ownerEmail\":\"%s\"}".formatted(admin)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("OWNER"));
  }

  @Test
  void pendingPlatformAdminCanBeInvitedToProject() throws Exception {
    // Given
    String owner = "invite-owner-admin@example.com";
    String pendingAdmin = "pending-admin-invited@example.com";
    createApprovedAdmin(owner);
    createPending(pendingAdmin, PlatformRole.ADMIN);
    Cookie session = loginAs(owner);
    long projectId = createProjectAs(session, "Projekt mit Gast", owner);

    // When / Then: direkt Mitglied, kein 422.
    mvc.perform(
            post("/api/projects/" + projectId + "/invitations")
                .cookie(session)
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"role\":\"MEMBER\"}".formatted(pendingAdmin)))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("added"));
  }

  @Test
  void pendingUserIsStillRejectedAsProjectOwner() throws Exception {
    // Given: wartender Nutzer mit Rolle USER — der Regelfall, der weiterhin greifen muss.
    String admin = "reject-owner-admin@example.com";
    String pendingUser = "pending-user-owner@example.com";
    createApprovedAdmin(admin);
    createPending(pendingUser, PlatformRole.USER);
    Cookie session = loginAs(admin);

    // When / Then
    mvc.perform(
            post("/api/projects")
                .cookie(session)
                .contentType("application/json")
                .content(
                    "{\"name\":\"Fremdes Projekt\",\"ownerEmail\":\"%s\"}".formatted(pendingUser)))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void pendingUserIsStillRejectedOnInvitation() throws Exception {
    // Given
    String admin = "reject-invite-admin@example.com";
    String pendingUser = "pending-user-invited@example.com";
    createApprovedAdmin(admin);
    createPending(pendingUser, PlatformRole.USER);
    Cookie session = loginAs(admin);
    long projectId = createProjectAs(session, "Geschlossenes Projekt", admin);

    // When / Then
    mvc.perform(
            post("/api/projects/" + projectId + "/invitations")
                .cookie(session)
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"role\":\"MEMBER\"}".formatted(pendingUser)))
        .andExpect(status().isUnprocessableEntity());
  }
}
