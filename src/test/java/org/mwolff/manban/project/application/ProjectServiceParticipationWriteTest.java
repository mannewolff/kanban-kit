package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.project.domain.Project;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Wer die Teilnahme am Plattform-Leitstand schalten darf (Issue #1077, AK 16, Plan #1072 E6).
 *
 * <p>Die Gegenrichtung zu {@link ProjectServiceParticipationViewTest}: Jene Klasse prüft, was die
 * {@link ProjectService.ProjectView} über die Teilnahme <em>aussagt</em>, diese, wer sie
 * <em>umlegen</em> darf.
 *
 * <p>Der interessante Fall ist der Plattform-Admin ohne eigene Mitgliedschaft. Er passiert sonst
 * jede Rechteprüfung des Projekts — hier nicht: Die Teilnahme ist die Einwilligung des Projekts,
 * und wer sie einholt, erteilt sie sich nicht selbst. Deshalb steht in {@link
 * ProjectService#setDashboardParticipation} {@code requireRealRole} und keine der Prüfungen mit
 * Bypass.
 */
class ProjectServiceParticipationWriteTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private ProjectRepository projects;
  private ProjectMembershipRepository memberships;
  private PermissionChecker permissions;
  private ProjectService service;

  private static ProjectMembership membership(long projectId, long userId, ProjectRole role) {
    return new ProjectMembership(1L, projectId, userId, role, FIXED);
  }

  /** Das Projekt, wie es nach dem Schalten aus der Persistenz zurückkommt. */
  private void projektMitTeilnahme(boolean teilnahme) {
    when(projects.findById(9L))
        .thenReturn(Optional.of(new Project(9L, "P", 1L, FIXED, null, teilnahme)));
  }

  @BeforeEach
  void setUp() {
    projects = mock(ProjectRepository.class);
    memberships = mock(ProjectMembershipRepository.class);
    permissions = mock(PermissionChecker.class);
    UserLookup users = mock(UserLookup.class);
    InvitationMailer mailer = mock(InvitationMailer.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    AuthProperties authProperties =
        new AuthProperties("https://app.example", null, null, null, null, null);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    service =
        new ProjectService(
            projects, memberships, permissions, users, mailer, events, authProperties, clock);
  }

  @Test
  void setDashboardParticipation_pruefteDieEchteRolleOhneBypass() {
    // Given
    projektMitTeilnahme(true);
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(9L, 2L, ProjectRole.OWNER)));

    // When
    service.setDashboardParticipation(2L, 9L, true);

    // Then
    verify(permissions).requireRealRole(2L, 9L, ProjectRole.OWNER, ProjectRole.ADMIN);
  }

  @Test
  void setDashboardParticipation_schreibtGezielt_ohneSave() {
    // Given
    projektMitTeilnahme(true);
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(9L, 2L, ProjectRole.OWNER)));

    // When
    service.setDashboardParticipation(2L, 9L, true);

    // Then
    verify(projects).setDashboardParticipation(9L, true);
    verify(projects, never()).save(org.mockito.ArgumentMatchers.any(Project.class));
  }

  @Test
  void setDashboardParticipation_gibtDenNeuenWertInDerSichtZurueck() {
    // Given
    projektMitTeilnahme(true);
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(9L, 2L, ProjectRole.OWNER)));

    // When
    ProjectService.ProjectView view = service.setDashboardParticipation(2L, 9L, true);

    // Then
    assertThat(view.dashboardParticipation()).isTrue();
    assertThat(view.participationEditable()).isTrue();
    assertThat(view.role()).isEqualTo(ProjectRole.OWNER);
  }

  @Test
  void setDashboardParticipation_schaltetAuchWiederAus() {
    // Given
    projektMitTeilnahme(false);
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(9L, 2L, ProjectRole.ADMIN)));

    // When
    ProjectService.ProjectView view = service.setDashboardParticipation(2L, 9L, false);

    // Then
    verify(projects).setDashboardParticipation(9L, false);
    assertThat(view.dashboardParticipation()).isFalse();
  }

  /**
   * AK 16, alle fünf Fälle an einer Stelle: OWNER und ADMIN dürfen, MEMBER und VIEWER bekommen 403,
   * ein Plattform-Admin ohne echte Mitgliedschaft 404. Die Unterscheidung trifft {@code
   * requireRealRole}; hier steht, dass der Use-Case sie durchreicht und bei einem Nein <b>nicht</b>
   * schreibt.
   */
  @Test
  void setDashboardParticipation_reichtDasNeinDurch_undSchreibtNicht() {
    // Given
    org.mockito.Mockito.doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .requireRealRole(2L, 9L, ProjectRole.OWNER, ProjectRole.ADMIN);

    // When / Then
    assertThatThrownBy(() -> service.setDashboardParticipation(2L, 9L, true))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(projects, never()).setDashboardParticipation(9L, true);
  }

  @Test
  void setDashboardParticipation_reichtDasUnbekannteProjektDurch_undSchreibtNicht() {
    // Given
    org.mockito.Mockito.doThrow(new ProjectNotFoundException())
        .when(permissions)
        .requireRealRole(1L, 9L, ProjectRole.OWNER, ProjectRole.ADMIN);

    // When / Then
    assertThatThrownBy(() -> service.setDashboardParticipation(1L, 9L, true))
        .isInstanceOf(ProjectNotFoundException.class);
    verify(projects, never()).setDashboardParticipation(9L, true);
  }
}
