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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** End-to-End-Test der Mitglieder-Einladung und -Verwaltung. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class MembershipIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

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

    @Override
    public void sendInvitationEmail(String toEmail, String projectName, String invitationUrl) {
      this.lastUrl = invitationUrl;
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
    Cookie bob = loginAs("member-inv@example.com");
    long projectId = createProject("owner-inv@example.com", "Team");

    mvc.perform(
            post("/api/projects/" + projectId + "/invitations")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"email\":\"member-inv@example.com\",\"role\":\"MEMBER\"}"))
        .andExpect(status().isAccepted());

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
            (List<Object>)
                JsonPath.read(membersBody, "$[?(@.email=='member-inv@example.com')].role"))
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
}
