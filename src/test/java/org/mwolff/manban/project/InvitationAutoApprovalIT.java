package org.mwolff.manban.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.auth.web.security.SessionCookieManager;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.common.SecureTokens;
import org.mwolff.manban.project.application.ProjectInvitationRepository;
import org.mwolff.manban.project.application.ProjectRepository;
import org.mwolff.manban.project.domain.Project;
import org.mwolff.manban.project.domain.ProjectInvitation;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-End: Eine E-Mail, für die eine offene Einladung vorliegt, wird bei der Registrierung
 * automatisch freigegeben (Issue #0099); eine nicht eingeladene bleibt pending. Registriert wird
 * ohne Passwort, angemeldet per Code (fachliche Quelle #1781, AK 14).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class InvitationAutoApprovalIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectRepository projects;
  @Autowired private ProjectInvitationRepository invitations;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Clock clock;

  /** Legt eine offene Einladung an und liefert das Klartext-Token aus dem Einladungslink. */
  private String createOpenInvitation(String email) {
    long ownerId =
        users
            .save(new AppUser(null, "owner-" + email, "Owner", true, PlatformRole.USER))
            .requireId();
    long projectId =
        projects
            .save(new Project(null, "Projekt", ownerId, clock.instant(), null, false, null))
            .requireId();
    String token = SecureTokens.newToken();
    invitations.save(
        new ProjectInvitation(
            null,
            projectId,
            email,
            ProjectRole.MEMBER,
            SecureTokens.sha256Hex(token),
            clock.instant().plusSeconds(3600),
            null,
            ownerId));
    return token;
  }

  private void register(String email) throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"displayName\":\"Neu\"}".formatted(email)))
        .andExpect(status().isCreated());
  }

  /** Meldet sich mit dem Code aus der Registrierungsmail an und liefert das Session-Cookie. */
  private Cookie loginWithCode(String email) throws Exception {
    String payload =
        jdbc.queryForObject(
            "SELECT payload FROM outbox_entry WHERE event_type = 'mail.login-code'"
                + " ORDER BY id DESC LIMIT 1",
            String.class);
    assertThat(payload).isNotNull();
    String code = PayloadFields.split(payload, 3).get(1);
    Cookie session =
        mvc.perform(
                post("/api/auth/code/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, code)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie(SessionCookieManager.COOKIE_NAME);
    assertThat(session).isNotNull();
    return session;
  }

  @Test
  void invitedEmailIsAutoApprovedOnRegistration() throws Exception {
    createOpenInvitation("invited@example.com");

    register("invited@example.com");

    assertThat(users.findByEmail("invited@example.com").orElseThrow().approvedAt()).isNotNull();
  }

  @Test
  void uninvitedEmailStaysPendingOnRegistration() throws Exception {
    register("stranger@example.com");

    assertThat(users.findByEmail("stranger@example.com").orElseThrow().approvedAt()).isNull();
  }

  @Test
  void invitedPersonRegistersWithoutPasswordLogsInWithCodeAndAcceptsInvitation() throws Exception {
    // Ein Admin besteht: Ohne die automatische Freigabe wiese die Code-Anmeldung das Konto ab.
    users.save(new AppUser(null, "admin@example.com", "Admin", true, PlatformRole.ADMIN));
    String token = createOpenInvitation("invited@example.com");

    register("invited@example.com");
    Cookie session = loginWithCode("invited@example.com");
    mvc.perform(
            post("/api/invitations/accept")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("MEMBER"));

    AppUser invited = users.findByEmail("invited@example.com").orElseThrow();
    assertThat(invited.approvedAt()).isNotNull();
    assertThat(invited.emailVerified()).isTrue();
  }
}
