package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import org.mwolff.manban.project.domain.Project;
import org.mwolff.manban.project.domain.ProjectInvitation;
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
}
