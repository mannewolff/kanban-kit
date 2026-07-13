package org.mwolff.manban.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.SecureTokens;
import org.mwolff.manban.project.application.InvitationMailer;
import org.mwolff.manban.project.application.ProjectInvitationRepository;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectInvitation;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-End-Test der Mitglieder-Einladung und -Verwaltung. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class MembershipIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @TestConfiguration
  static class MailTestConfig {
    @Bean
    @Primary
    CapturingInvitationMailer capturingInvitationMailer() {
      return new CapturingInvitationMailer();
    }
  }

  static class CapturingInvitationMailer implements InvitationMailer {
    volatile String lastUrl;
    volatile String lastAssignedEmail;

    @Override
    public void sendInvitationEmail(String toEmail, String projectName, String invitationUrl) {
      this.lastUrl = invitationUrl;
    }

    @Override
    public void sendProjectAssignedEmail(
        String toEmail, String projectName, ProjectRole role, String projectUrl) {
      this.lastAssignedEmail = toEmail;
    }

    String lastToken() {
      return lastUrl.substring(lastUrl.indexOf("token=") + "token=".length());
    }
  }

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private ProjectInvitationRepository invitations;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private CapturingInvitationMailer mailer;

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().id();
  }

  private Cookie loginAs(String email) throws Exception {
    if (users.findByEmail(email).isEmpty()) {
      users.save(
          new AppUser(
              null, email, passwordEncoder.encode(PASSWORD), "Person", true, PlatformRole.USER));
    }
    return mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("manban_session");
  }

  private long createProject(String ownerEmail, String name) throws Exception {
    if (users.findByEmail(ownerEmail).isEmpty()) {
      users.save(
          new AppUser(
              null,
              ownerEmail,
              passwordEncoder.encode(PASSWORD),
              "Person",
              true,
              PlatformRole.USER));
    }
    Cookie admin = platformAdminSession();
    String body =
        mvc.perform(
                post("/api/projects")
                    .cookie(admin)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\",\"ownerEmail\":\"%s\"}".formatted(name, ownerEmail)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private Cookie platformAdminSession() throws Exception {
    String email = "project-admin@example.com";
    if (users.findByEmail(email).isEmpty()) {
      users.save(
          new AppUser(
              null, email, passwordEncoder.encode(PASSWORD), "Person", true, PlatformRole.ADMIN));
    }
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
  void inviteAcceptFlowGrantsCorrectRole() throws Exception {
    Cookie alice = loginAs("owner-inv@example.com");
    long projectId = createProject("owner-inv@example.com", "Team");

    // Noch unbekannte E-Mail -> Einladungs-/Token-Pfad (Antwort "invited").
    mvc.perform(
            post("/api/projects/" + projectId + "/invitations")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"email\":\"member-inv@example.com\",\"role\":\"MEMBER\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("invited"));

    // Erst danach registriert sich der Eingeladene und nimmt per Token an.
    Cookie bob = loginAs("member-inv@example.com");
    mvc.perform(
            post("/api/invitations/accept")
                .cookie(bob)
                .contentType("application/json")
                .content("{\"token\":\"%s\"}".formatted(mailer.lastToken())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("MEMBER"))
        .andExpect(jsonPath("$.email").value("member-inv@example.com"));

    String membersBody =
        mvc.perform(get("/api/projects/" + projectId + "/members").cookie(alice))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(
            JsonPath.<List<Object>>read(
                membersBody, "$[?(@.email=='member-inv@example.com')].role"))
        .contains("MEMBER");
  }

  @Test
  void expiredInvitationIsRejected() throws Exception {
    loginAs("owner-exp@example.com");
    Cookie carol = loginAs("carol-exp@example.com");
    long projectId = createProject("owner-exp@example.com", "Expired");

    String plaintext = "abgelaufene-einladung";
    invitations.save(
        new ProjectInvitation(
            null,
            projectId,
            "carol-exp@example.com",
            ProjectRole.MEMBER,
            SecureTokens.sha256Hex(plaintext),
            Instant.now().minusSeconds(3600),
            null,
            userId("owner-exp@example.com")));

    mvc.perform(
            post("/api/invitations/accept")
                .cookie(carol)
                .contentType("application/json")
                .content("{\"token\":\"%s\"}".formatted(plaintext)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void lastOwnerCannotBeRemoved() throws Exception {
    Cookie alice = loginAs("solo-owner@example.com");
    long projectId = createProject("solo-owner@example.com", "Solo");

    mvc.perform(
            delete("/api/projects/" + projectId + "/members/" + userId("solo-owner@example.com"))
                .cookie(alice))
        .andExpect(status().isConflict());
  }

  @Test
  void memberWithoutInvitePermissionCannotInvite() throws Exception {
    loginAs("owner-perm@example.com");
    Cookie bob = loginAs("plain-member@example.com");
    long projectId = createProject("owner-perm@example.com", "Perm");
    memberships.save(
        new ProjectMembership(
            null,
            projectId,
            userId("plain-member@example.com"),
            ProjectRole.MEMBER,
            Instant.now()));

    mvc.perform(
            post("/api/projects/" + projectId + "/invitations")
                .cookie(bob)
                .contentType("application/json")
                .content("{\"email\":\"x@example.com\",\"role\":\"MEMBER\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void acceptWithMismatchedEmailIsForbidden() throws Exception {
    Cookie alice = loginAs("owner-mism@example.com");
    Cookie mallory = loginAs("mallory@example.com");
    long projectId = createProject("owner-mism@example.com", "Mismatch");

    mvc.perform(
            post("/api/projects/" + projectId + "/invitations")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"email\":\"intended@example.com\",\"role\":\"MEMBER\"}"))
        .andExpect(status().isAccepted());

    // Mallory (andere E-Mail) versucht, die Einladung anzunehmen.
    mvc.perform(
            post("/api/invitations/accept")
                .cookie(mallory)
                .contentType("application/json")
                .content("{\"token\":\"%s\"}".formatted(mailer.lastToken())))
        .andExpect(status().isForbidden());
  }

  @Test
  void ownerCanChangeMemberRoleAndRemove() throws Exception {
    Cookie alice = loginAs("owner-mgmt@example.com");
    loginAs("target-mgmt@example.com");
    long projectId = createProject("owner-mgmt@example.com", "Manage");
    long targetId = userId("target-mgmt@example.com");
    memberships.save(
        new ProjectMembership(null, projectId, targetId, ProjectRole.MEMBER, Instant.now()));

    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/api/projects/" + projectId + "/members/" + targetId)
                .cookie(alice)
                .contentType("application/json")
                .content("{\"role\":\"ADMIN\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"));

    mvc.perform(delete("/api/projects/" + projectId + "/members/" + targetId).cookie(alice))
        .andExpect(status().isNoContent());
  }

  @Test
  void inviteRegisteredApprovedUserAddsDirectly() throws Exception {
    Cookie alice = loginAs("owner-dir@example.com");
    loginAs("member-dir@example.com"); // registriert + freigegeben
    long projectId = createProject("owner-dir@example.com", "Direct");

    mvc.perform(
            post("/api/projects/" + projectId + "/invitations")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"email\":\"member-dir@example.com\",\"role\":\"MEMBER\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("added"));

    // Direkt Mitglied, ohne Accept-Schritt.
    String membersBody =
        mvc.perform(get("/api/projects/" + projectId + "/members").cookie(alice))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(
            JsonPath.<List<Object>>read(
                membersBody, "$[?(@.email=='member-dir@example.com')].role"))
        .contains("MEMBER");
  }

  @Test
  void invitePendingUserIsRejected() throws Exception {
    Cookie alice = loginAs("owner-pend@example.com");
    long projectId = createProject("owner-pend@example.com", "Pending");
    // Registrierter, aber noch nicht freigegebener Nutzer (approvedAt=null).
    users.save(
        new AppUser(
            null,
            "pending@example.com",
            passwordEncoder.encode(PASSWORD),
            "Pending",
            true,
            PlatformRole.USER,
            null,
            null));

    mvc.perform(
            post("/api/projects/" + projectId + "/invitations")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"email\":\"pending@example.com\",\"role\":\"MEMBER\"}"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void transferOwnershipPromotesTargetAndDemotesCaller() throws Exception {
    Cookie alice = loginAs("xfer-owner@example.com");
    loginAs("xfer-member@example.com");
    long projectId = createProject("xfer-owner@example.com", "OwnerXfer");
    long targetId = userId("xfer-member@example.com");
    memberships.save(
        new ProjectMembership(null, projectId, targetId, ProjectRole.MEMBER, Instant.now()));

    mvc.perform(
            post("/api/projects/" + projectId + "/owner")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"newOwnerUserId\":%d}".formatted(targetId)))
        .andExpect(status().isOk());

    assertThat(memberships.findByProjectIdAndUserId(projectId, targetId).orElseThrow().role())
        .isEqualTo(ProjectRole.OWNER);
    assertThat(
            memberships
                .findByProjectIdAndUserId(projectId, userId("xfer-owner@example.com"))
                .orElseThrow()
                .role())
        .isEqualTo(ProjectRole.ADMIN);
  }

  @Test
  void transferOwnershipForbiddenForNonOwner() throws Exception {
    loginAs("xfer2-owner@example.com");
    Cookie mallory = loginAs("xfer2-member@example.com");
    long projectId = createProject("xfer2-owner@example.com", "OwnerXfer2");
    long malloryId = userId("xfer2-member@example.com");
    memberships.save(
        new ProjectMembership(null, projectId, malloryId, ProjectRole.MEMBER, Instant.now()));

    mvc.perform(
            post("/api/projects/" + projectId + "/owner")
                .cookie(mallory)
                .contentType("application/json")
                .content("{\"newOwnerUserId\":%d}".formatted(malloryId)))
        .andExpect(status().isForbidden());
  }
}
