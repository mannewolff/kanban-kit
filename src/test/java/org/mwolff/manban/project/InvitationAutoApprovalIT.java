package org.mwolff.manban.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.SecureTokens;
import org.mwolff.manban.project.application.ProjectInvitationRepository;
import org.mwolff.manban.project.application.ProjectRepository;
import org.mwolff.manban.project.domain.Project;
import org.mwolff.manban.project.domain.ProjectInvitation;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-End: Eine E-Mail, für die eine offene Einladung vorliegt, wird bei der Registrierung
 * automatisch freigegeben (Issue #0099); eine nicht eingeladene bleibt pending.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class InvitationAutoApprovalIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectRepository projects;
  @Autowired private ProjectInvitationRepository invitations;
  @Autowired private PasswordEncoder encoder;
  @Autowired private Clock clock;

  private void createOpenInvitation(String email) {
    long ownerId =
        users
            .save(
                new AppUser(
                    null,
                    "owner-" + email,
                    encoder.encode(PASSWORD),
                    "Owner",
                    true,
                    PlatformRole.USER))
            .requireId();
    long projectId =
        projects.save(new Project(null, "Projekt", ownerId, clock.instant())).requireId();
    invitations.save(
        new ProjectInvitation(
            null,
            projectId,
            email,
            ProjectRole.MEMBER,
            SecureTokens.sha256Hex(SecureTokens.newToken()),
            clock.instant().plusSeconds(3600),
            null,
            ownerId));
  }

  private void register(String email) throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content(
                    "{\"email\":\"%s\",\"password\":\"%s\",\"displayName\":\"Neu\"}"
                        .formatted(email, PASSWORD)))
        .andExpect(status().isCreated());
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
}
