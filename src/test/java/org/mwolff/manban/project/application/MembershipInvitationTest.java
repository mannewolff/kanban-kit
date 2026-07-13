package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.project.domain.Project;
import org.mwolff.manban.project.domain.ProjectInvitation;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;

/** Verhaltenstests des Einladens (invite) — Mockito an den Ports. */
class MembershipInvitationTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private ProjectRepository projects;
  private ProjectMembershipRepository memberships;
  private ProjectInvitationRepository invitations;
  private PermissionChecker permissions;
  private InvitationMailer mailer;
  private AppUserRepository users;
  private MembershipService service;

  @BeforeEach
  void setUp() {
    projects = mock(ProjectRepository.class);
    memberships = mock(ProjectMembershipRepository.class);
    invitations = mock(ProjectInvitationRepository.class);
    permissions = mock(PermissionChecker.class);
    mailer = mock(InvitationMailer.class);
    users = mock(AppUserRepository.class);
    AuthProperties authProperties =
        new AuthProperties("https://app.example", null, null, null, null, null);
    ProjectProperties projectProperties = new ProjectProperties(Duration.ofDays(7));
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    service =
        new MembershipService(
            projects,
            memberships,
            invitations,
            permissions,
            mailer,
            users,
            authProperties,
            projectProperties,
            clock);
  }

  @Test
  void invite_setsExpiryFromInjectedClockPlusTtl() {
    // Given
    when(projects.findById(9L)).thenReturn(Optional.of(new Project(9L, "P", 1L, FIXED)));
    when(invitations.save(any(ProjectInvitation.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    ArgumentCaptor<ProjectInvitation> captor = ArgumentCaptor.forClass(ProjectInvitation.class);
    service.invite(1L, 9L, "guest@x.de", ProjectRole.MEMBER);

    // Then
    verify(invitations).save(captor.capture());
    assertThat(captor.getValue().expiresAt()).isEqualTo(FIXED.plus(Duration.ofDays(7)));
  }

  @Test
  void invite_normalizesEmail() {
    // Given
    when(projects.findById(9L)).thenReturn(Optional.of(new Project(9L, "P", 1L, FIXED)));
    when(invitations.save(any(ProjectInvitation.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    ArgumentCaptor<ProjectInvitation> captor = ArgumentCaptor.forClass(ProjectInvitation.class);
    service.invite(1L, 9L, "  GUEST@x.de ", ProjectRole.MEMBER);

    // Then
    verify(invitations).save(captor.capture());
    assertThat(captor.getValue().email()).isEqualTo("guest@x.de");
  }

  @Test
  void invite_sendsInvitationEmail() {
    // Given
    when(projects.findById(9L)).thenReturn(Optional.of(new Project(9L, "P", 1L, FIXED)));
    when(invitations.save(any(ProjectInvitation.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    service.invite(1L, 9L, "guest@x.de", ProjectRole.MEMBER);

    // Then
    verify(mailer).sendInvitationEmail(eq("guest@x.de"), eq("P"), anyString());
  }

  @Test
  void invite_throwsProjectNotFound_whenProjectMissing() {
    // Given
    when(projects.findById(9L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.invite(1L, 9L, "guest@x.de", ProjectRole.MEMBER))
        .isInstanceOf(ProjectNotFoundException.class);
  }

  @Test
  void invite_returnsInvited_forUnknownEmail() {
    // Given: keine Registrierung (users.findByEmail Default: empty).
    when(projects.findById(9L)).thenReturn(Optional.of(new Project(9L, "P", 1L, FIXED)));
    when(invitations.save(any(ProjectInvitation.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    InviteOutcome outcome = service.invite(1L, 9L, "guest@x.de", ProjectRole.MEMBER);

    // Then
    assertThat(outcome).isEqualTo(InviteOutcome.INVITED);
  }

  @Test
  void invite_addsRegisteredApprovedUserDirectly_andReturnsAdded() {
    // Given: E-Mail gehört zu einem registrierten, freigegebenen Nutzer.
    when(projects.findById(9L)).thenReturn(Optional.of(new Project(9L, "P", 1L, FIXED)));
    when(users.findByEmail("bob@x.de"))
        .thenReturn(Optional.of(new AppUser(7L, "bob@x.de", "h", "Bob", true, PlatformRole.USER)));
    when(memberships.findByProjectIdAndUserId(9L, 7L)).thenReturn(Optional.empty());
    when(memberships.save(any(ProjectMembership.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    ArgumentCaptor<ProjectMembership> captor = ArgumentCaptor.forClass(ProjectMembership.class);
    InviteOutcome outcome = service.invite(1L, 9L, "bob@x.de", ProjectRole.MEMBER);

    // Then: direkte Mitgliedschaft, keine Einladung, Info-Mail.
    assertThat(outcome).isEqualTo(InviteOutcome.ADDED);
    verify(memberships).save(captor.capture());
    assertThat(captor.getValue().role()).isEqualTo(ProjectRole.MEMBER);
    verify(invitations, never()).save(any(ProjectInvitation.class));
    verify(mailer)
        .sendProjectAssignedEmail(
            eq("bob@x.de"), eq("P"), eq(ProjectRole.MEMBER), eq("https://app.example/projects/9"));
  }

  @Test
  void invite_updatesRole_forExistingMember() {
    // Given: Nutzer ist bereits Mitglied (VIEWER) — invite mit ADMIN aktualisiert die Rolle.
    when(projects.findById(9L)).thenReturn(Optional.of(new Project(9L, "P", 1L, FIXED)));
    when(users.findByEmail("bob@x.de"))
        .thenReturn(Optional.of(new AppUser(7L, "bob@x.de", "h", "Bob", true, PlatformRole.USER)));
    when(memberships.findByProjectIdAndUserId(9L, 7L))
        .thenReturn(Optional.of(new ProjectMembership(5L, 9L, 7L, ProjectRole.VIEWER, FIXED)));
    when(memberships.save(any(ProjectMembership.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    ArgumentCaptor<ProjectMembership> captor = ArgumentCaptor.forClass(ProjectMembership.class);
    InviteOutcome outcome = service.invite(1L, 9L, "bob@x.de", ProjectRole.ADMIN);

    // Then: bestehende Mitgliedschaft (id 5) auf ADMIN aktualisiert.
    assertThat(outcome).isEqualTo(InviteOutcome.ADDED);
    verify(memberships).save(captor.capture());
    assertThat(captor.getValue().id()).isEqualTo(5L);
    assertThat(captor.getValue().role()).isEqualTo(ProjectRole.ADMIN);
  }

  @Test
  void invite_throwsMemberNotApproved_forRegisteredPendingUser() {
    // Given: registrierter, aber nicht freigegebener Nutzer (approvedAt=null).
    when(projects.findById(9L)).thenReturn(Optional.of(new Project(9L, "P", 1L, FIXED)));
    when(users.findByEmail("bob@x.de"))
        .thenReturn(
            Optional.of(
                new AppUser(7L, "bob@x.de", "h", "Bob", true, PlatformRole.USER, null, null)));

    // When / Then
    assertThatThrownBy(() -> service.invite(1L, 9L, "bob@x.de", ProjectRole.MEMBER))
        .isInstanceOf(MemberNotApprovedException.class);
  }

  @Test
  void invite_returnsAddedStatusStrings() {
    // Deckt die InviteOutcome.status()-Abbildung ab (added/invited).
    assertThat(InviteOutcome.ADDED.status()).isEqualTo("added");
    assertThat(InviteOutcome.INVITED.status()).isEqualTo("invited");
  }
}
