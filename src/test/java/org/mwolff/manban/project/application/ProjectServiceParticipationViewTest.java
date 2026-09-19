package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.auth.application.UserSummary;
import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.project.domain.Project;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Was die {@link ProjectService.ProjectView} über die Teilnahme am Plattform-Leitstand aussagt
 * (Issue #1076, Plan #1072 E7).
 *
 * <p>Zwei Felder, zwei verschiedene Fragen: {@code dashboardParticipation} ist der gespeicherte
 * Zustand des Projekts, {@code participationEditable} die Antwort auf „darf dieser Aufrufer ihn
 * umlegen?". Letzteres kann der Browser nicht selbst ausrechnen, weil {@link ProjectService#list}
 * einem Plattform-Admin ohne Mitgliedschaft die synthetische Rolle {@code OWNER} setzt — aus {@code
 * role} ist die echte Mitgliedschaft nicht ablesbar.
 *
 * <p>Eigene Klasse statt weiterer Tests in {@link ProjectServiceTest} (Issue #1076): Jene Klasse
 * steht an der PMD-Schwelle {@code TooManyMethods} (30, {@code config/pmd/ruleset.xml}), und diese
 * neun Tests beantworten eine Frage für sich.
 */
class ProjectServiceParticipationViewTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private ProjectRepository projects;
  private ProjectMembershipRepository memberships;
  private PermissionChecker permissions;
  private UserLookup users;
  private ProjectService service;

  private static ProjectMembership membership(long projectId, long userId, ProjectRole role) {
    return new ProjectMembership(1L, projectId, userId, role, FIXED);
  }

  @BeforeEach
  void setUp() {
    projects = mock(ProjectRepository.class);
    memberships = mock(ProjectMembershipRepository.class);
    permissions = mock(PermissionChecker.class);
    users = mock(UserLookup.class);
    InvitationMailer mailer = mock(InvitationMailer.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    AuthProperties authProperties =
        new AuthProperties("https://app.example", null, null, null, null, null);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    service =
        new ProjectService(
            projects, memberships, permissions, users, mailer, events, authProperties, clock);
  }

  /**
   * Ein frisch angelegtes Projekt nimmt nicht am Plattform-Leitstand teil (Issue #1076, AK 17); der
   * Owner, der es gerade erhalten hat, darf die Teilnahme aber schalten.
   */
  @Test
  void create_returnsView_withParticipationOffAndEditableByOwner() {
    // Given
    when(permissions.isPlatformAdmin(1L)).thenReturn(true);
    when(users.findByEmail("owner@x.de"))
        .thenReturn(Optional.of(new UserSummary(2L, "owner@x.de", "Owner", true)));
    when(projects.save(any(Project.class)))
        .thenAnswer(
            inv -> {
              Project p = inv.getArgument(0);
              return new Project(
                  9L,
                  p.name(),
                  p.ownerUserId(),
                  p.createdAt(),
                  p.interactiveUsageSince(),
                  p.dashboardParticipation());
            });

    // When
    ProjectService.ProjectView view = service.create(1L, "Neu", "owner@x.de");

    // Then
    assertThat(view.dashboardParticipation()).isFalse();
    assertThat(view.participationEditable()).isTrue();
  }

  @Test
  void list_returnsDashboardParticipationFromProject() {
    // Given
    when(permissions.isPlatformAdmin(2L)).thenReturn(false);
    when(memberships.findByUserId(2L)).thenReturn(List.of(membership(9L, 2L, ProjectRole.MEMBER)));
    when(projects.findById(9L))
        .thenReturn(Optional.of(new Project(9L, "P", 1L, FIXED, null, true)));

    // When
    List<ProjectService.ProjectView> result = service.list(2L);

    // Then
    assertThat(result)
        .singleElement()
        .extracting(ProjectService.ProjectView::dashboardParticipation)
        .isEqualTo(true);
  }

  @Test
  void list_marksParticipationEditable_forRealOwner() {
    // Given
    when(permissions.isPlatformAdmin(2L)).thenReturn(false);
    when(memberships.findByUserId(2L)).thenReturn(List.of(membership(9L, 2L, ProjectRole.OWNER)));
    when(projects.findById(9L))
        .thenReturn(Optional.of(new Project(9L, "P", 2L, FIXED, null, false)));

    // When
    List<ProjectService.ProjectView> result = service.list(2L);

    // Then
    assertThat(result)
        .singleElement()
        .extracting(ProjectService.ProjectView::participationEditable)
        .isEqualTo(true);
  }

  @Test
  void list_marksParticipationEditable_forRealAdmin() {
    // Given
    when(permissions.isPlatformAdmin(2L)).thenReturn(false);
    when(memberships.findByUserId(2L)).thenReturn(List.of(membership(9L, 2L, ProjectRole.ADMIN)));
    when(projects.findById(9L))
        .thenReturn(Optional.of(new Project(9L, "P", 1L, FIXED, null, false)));

    // When
    List<ProjectService.ProjectView> result = service.list(2L);

    // Then
    assertThat(result)
        .singleElement()
        .extracting(ProjectService.ProjectView::participationEditable)
        .isEqualTo(true);
  }

  @Test
  void list_marksParticipationNotEditable_forMember() {
    // Given
    when(permissions.isPlatformAdmin(2L)).thenReturn(false);
    when(memberships.findByUserId(2L)).thenReturn(List.of(membership(9L, 2L, ProjectRole.MEMBER)));
    when(projects.findById(9L))
        .thenReturn(Optional.of(new Project(9L, "P", 1L, FIXED, null, false)));

    // When
    List<ProjectService.ProjectView> result = service.list(2L);

    // Then
    assertThat(result)
        .singleElement()
        .extracting(ProjectService.ProjectView::participationEditable)
        .isEqualTo(false);
  }

  @Test
  void list_marksParticipationNotEditable_forViewer() {
    // Given
    when(permissions.isPlatformAdmin(2L)).thenReturn(false);
    when(memberships.findByUserId(2L)).thenReturn(List.of(membership(9L, 2L, ProjectRole.VIEWER)));
    when(projects.findById(9L))
        .thenReturn(Optional.of(new Project(9L, "P", 1L, FIXED, null, false)));

    // When
    List<ProjectService.ProjectView> result = service.list(2L);

    // Then
    assertThat(result)
        .singleElement()
        .extracting(ProjectService.ProjectView::participationEditable)
        .isEqualTo(false);
  }

  /**
   * Ein Plattform-Admin ohne eigene Mitgliedschaft sieht das Projekt ueber die synthetische
   * OWNER-Rolle (siehe {@link ProjectServiceTest#list_returnsAllProjects_forPlatformAdmin()}) —
   * schalten darf er die Teilnahme trotzdem nicht, weil er kein echtes Mitglied ist (Plan #1072
   * E7).
   */
  @Test
  void list_marksParticipationNotEditable_forPlatformAdminWithoutMembership() {
    // Given
    when(permissions.isPlatformAdmin(1L)).thenReturn(true);
    when(memberships.findByUserId(1L)).thenReturn(List.of());
    when(projects.findAll()).thenReturn(List.of(new Project(9L, "P", 2L, FIXED, null, false)));

    // When
    List<ProjectService.ProjectView> result = service.list(1L);

    // Then
    assertThat(result)
        .singleElement()
        .extracting(ProjectService.ProjectView::participationEditable)
        .isEqualTo(false);
  }

  @Test
  void rename_marksParticipationEditable_forRealOwner() {
    // Given
    when(permissions.require(2L, 9L, Permission.PROJECT_EDIT))
        .thenReturn(membership(9L, 2L, ProjectRole.OWNER));
    when(permissions.isRealProjectMember(2L, 9L)).thenReturn(true);
    when(projects.findById(9L))
        .thenReturn(Optional.of(new Project(9L, "Alt", 2L, FIXED, null, false)));
    when(projects.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    ProjectService.ProjectView view = service.rename(2L, 9L, "Neu");

    // Then
    assertThat(view.participationEditable()).isTrue();
  }

  /**
   * Ein Plattform-Admin ohne echte Mitgliedschaft darf umbenennen (synthetische OWNER-Rolle), aber
   * nicht die Teilnahme schalten (Plan #1072 E7).
   */
  @Test
  void rename_marksParticipationNotEditable_forPlatformAdminWithoutRealMembership() {
    // Given
    when(permissions.require(1L, 9L, Permission.PROJECT_EDIT))
        .thenReturn(membership(9L, 1L, ProjectRole.OWNER));
    when(permissions.isRealProjectMember(1L, 9L)).thenReturn(false);
    when(projects.findById(9L))
        .thenReturn(Optional.of(new Project(9L, "Alt", 2L, FIXED, null, false)));
    when(projects.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    ProjectService.ProjectView view = service.rename(1L, 9L, "Neu");

    // Then
    assertThat(view.participationEditable()).isFalse();
  }
}
