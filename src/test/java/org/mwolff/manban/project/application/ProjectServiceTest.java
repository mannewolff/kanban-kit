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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.project.domain.Project;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;

/** Verhaltenstests der Projekt-Use-Cases (Mockito an den Ports). */
class ProjectServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private ProjectRepository projects;
  private ProjectMembershipRepository memberships;
  private PermissionChecker permissions;
  private AppUserRepository users;
  private InvitationMailer mailer;
  private ProjectService service;

  private static ProjectMembership membership(long projectId, long userId, ProjectRole role) {
    return new ProjectMembership(1L, projectId, userId, role, FIXED);
  }

  @BeforeEach
  void setUp() {
    projects = mock(ProjectRepository.class);
    memberships = mock(ProjectMembershipRepository.class);
    permissions = mock(PermissionChecker.class);
    users = mock(AppUserRepository.class);
    mailer = mock(InvitationMailer.class);
    AuthProperties authProperties =
        new AuthProperties("https://app.example", null, null, null, null, null);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    service =
        new ProjectService(
            projects, memberships, permissions, users, mailer, authProperties, clock);
  }

  @Test
  void create_setsCreatedAtFromInjectedClock() {
    // Given
    when(permissions.isPlatformAdmin(1L)).thenReturn(true);
    when(users.findByEmail("owner@x.de"))
        .thenReturn(
            Optional.of(new AppUser(2L, "owner@x.de", "hash", "Owner", true, PlatformRole.USER)));
    when(projects.save(any(Project.class)))
        .thenAnswer(
            inv -> {
              Project p = inv.getArgument(0);
              return p.id() == null ? new Project(9L, p.name(), p.ownerUserId(), p.createdAt()) : p;
            });

    // When
    ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
    service.create(1L, "Neu", "owner@x.de");

    // Then
    verify(projects).save(captor.capture());
    assertThat(captor.getValue().createdAt()).isEqualTo(FIXED);
  }

  @Test
  void create_addsOwnerAsOwnerMember() {
    // Given
    when(permissions.isPlatformAdmin(1L)).thenReturn(true);
    when(users.findByEmail("owner@x.de"))
        .thenReturn(
            Optional.of(new AppUser(2L, "owner@x.de", "hash", "Owner", true, PlatformRole.USER)));
    when(projects.save(any(Project.class)))
        .thenAnswer(
            inv -> {
              Project p = inv.getArgument(0);
              return new Project(9L, p.name(), p.ownerUserId(), p.createdAt());
            });

    // When
    ArgumentCaptor<ProjectMembership> captor = ArgumentCaptor.forClass(ProjectMembership.class);
    service.create(1L, "Neu", "owner@x.de");

    // Then
    verify(memberships).save(captor.capture());
    assertThat(captor.getValue().role()).isEqualTo(ProjectRole.OWNER);
  }

  @Test
  void create_returnsViewOfCreatedProject() {
    // Given
    when(permissions.isPlatformAdmin(1L)).thenReturn(true);
    when(users.findByEmail("owner@x.de"))
        .thenReturn(
            Optional.of(new AppUser(2L, "owner@x.de", "hash", "Owner", true, PlatformRole.USER)));
    when(projects.save(any(Project.class)))
        .thenAnswer(
            inv -> {
              Project p = inv.getArgument(0);
              return new Project(9L, p.name(), p.ownerUserId(), p.createdAt());
            });

    // When
    ProjectService.ProjectView view = service.create(1L, "Neu", "owner@x.de");

    // Then
    assertThat(view.name()).isEqualTo("Neu");
  }

  @Test
  void create_sendsAssignmentInfoMailToOwner() {
    // Given
    when(permissions.isPlatformAdmin(1L)).thenReturn(true);
    when(users.findByEmail("owner@x.de"))
        .thenReturn(
            Optional.of(new AppUser(2L, "owner@x.de", "hash", "Owner", true, PlatformRole.USER)));
    when(projects.save(any(Project.class)))
        .thenAnswer(
            inv -> {
              Project p = inv.getArgument(0);
              return new Project(9L, p.name(), p.ownerUserId(), p.createdAt());
            });

    // When
    service.create(1L, "Neu", "owner@x.de");

    // Then
    verify(mailer)
        .sendProjectAssignedEmail(eq("owner@x.de"), eq("Neu"), eq(ProjectRole.OWNER), anyString());
  }

  @Test
  void create_normalizesOwnerEmailToLowercase() {
    // Given: gespeicherte E-Mail ist lowercase; der Aufruf liefert sie gemischt/mit Rand.
    when(permissions.isPlatformAdmin(1L)).thenReturn(true);
    when(users.findByEmail("owner@x.de"))
        .thenReturn(
            Optional.of(new AppUser(2L, "owner@x.de", "hash", "Owner", true, PlatformRole.USER)));
    when(projects.save(any(Project.class)))
        .thenAnswer(
            inv -> {
              Project p = inv.getArgument(0);
              return new Project(9L, p.name(), p.ownerUserId(), p.createdAt());
            });

    // When
    service.create(1L, "Neu", "  Owner@X.de ");

    // Then
    verify(users).findByEmail("owner@x.de");
  }

  @Test
  void create_throwsMemberNotApproved_whenOwnerPending() {
    // Given: Owner existiert, ist aber noch nicht freigegeben (approvedAt=null).
    when(permissions.isPlatformAdmin(1L)).thenReturn(true);
    when(users.findByEmail("owner@x.de"))
        .thenReturn(
            Optional.of(
                new AppUser(
                    2L, "owner@x.de", "hash", "Owner", true, PlatformRole.USER, null, null)));

    // When / Then
    assertThatThrownBy(() -> service.create(1L, "Neu", "owner@x.de"))
        .isInstanceOf(MemberNotApprovedException.class);
  }

  @Test
  void create_throwsAccessDenied_whenActorNotPlatformAdmin() {
    // Given
    when(permissions.isPlatformAdmin(1L)).thenReturn(false);

    // When / Then
    assertThatThrownBy(() -> service.create(1L, "Neu", "owner@x.de"))
        .isInstanceOf(ProjectAccessDeniedException.class);
  }

  @Test
  void create_throwsOwnerNotFound_whenOwnerEmailUnknown() {
    // Given
    when(permissions.isPlatformAdmin(1L)).thenReturn(true);
    when(users.findByEmail("owner@x.de")).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.create(1L, "Neu", "owner@x.de"))
        .isInstanceOf(ProjectOwnerNotFoundException.class);
  }

  @Test
  void list_returnsAllProjects_forPlatformAdmin() {
    // Given
    when(permissions.isPlatformAdmin(1L)).thenReturn(true);
    when(memberships.findByUserId(1L)).thenReturn(List.of());
    when(projects.findAll()).thenReturn(List.of(new Project(9L, "P", 2L, FIXED)));

    // When
    List<ProjectService.ProjectView> result = service.list(1L);

    // Then
    assertThat(result)
        .singleElement()
        .extracting(ProjectService.ProjectView::role)
        .isEqualTo(ProjectRole.OWNER);
  }

  @Test
  void list_returnsOnlyMemberProjects_forRegularUser() {
    // Given
    when(permissions.isPlatformAdmin(2L)).thenReturn(false);
    when(memberships.findByUserId(2L)).thenReturn(List.of(membership(9L, 2L, ProjectRole.MEMBER)));
    when(projects.findById(9L)).thenReturn(Optional.of(new Project(9L, "P", 1L, FIXED)));

    // When
    List<ProjectService.ProjectView> result = service.list(2L);

    // Then
    assertThat(result)
        .singleElement()
        .extracting(ProjectService.ProjectView::role)
        .isEqualTo(ProjectRole.MEMBER);
  }

  @Test
  void list_skipsProjectsThatCannotBeLoaded_forRegularUser() {
    // Given
    when(permissions.isPlatformAdmin(2L)).thenReturn(false);
    when(memberships.findByUserId(2L)).thenReturn(List.of(membership(9L, 2L, ProjectRole.MEMBER)));
    when(projects.findById(9L)).thenReturn(Optional.empty());

    // When
    List<ProjectService.ProjectView> result = service.list(2L);

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void rename_trimsAndPersistsName() {
    // Given
    when(permissions.require(2L, 9L, Permission.PROJECT_EDIT))
        .thenReturn(membership(9L, 2L, ProjectRole.OWNER));
    when(projects.findById(9L)).thenReturn(Optional.of(new Project(9L, "Alt", 2L, FIXED)));
    when(projects.save(any(Project.class)))
        .thenAnswer(
            inv -> {
              Project p = inv.getArgument(0);
              return p.id() == null ? new Project(9L, p.name(), p.ownerUserId(), p.createdAt()) : p;
            });

    // When
    ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
    service.rename(2L, 9L, "  Neu  ");

    // Then
    verify(projects).save(captor.capture());
    assertThat(captor.getValue().name()).isEqualTo("Neu");
  }

  @Test
  void rename_returnsViewWithNewName() {
    // Given
    when(permissions.require(2L, 9L, Permission.PROJECT_EDIT))
        .thenReturn(membership(9L, 2L, ProjectRole.OWNER));
    when(projects.findById(9L)).thenReturn(Optional.of(new Project(9L, "Alt", 2L, FIXED)));
    when(projects.save(any(Project.class)))
        .thenAnswer(
            inv -> {
              Project p = inv.getArgument(0);
              return p.id() == null ? new Project(9L, p.name(), p.ownerUserId(), p.createdAt()) : p;
            });

    // When
    ProjectService.ProjectView view = service.rename(2L, 9L, "Neu");

    // Then
    assertThat(view.name()).isEqualTo("Neu");
  }

  @Test
  void rename_throwsProjectNotFound_whenProjectMissing() {
    // Given
    when(permissions.require(2L, 9L, Permission.PROJECT_EDIT))
        .thenReturn(membership(9L, 2L, ProjectRole.OWNER));
    when(projects.findById(9L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.rename(2L, 9L, "Neu"))
        .isInstanceOf(ProjectNotFoundException.class);
  }

  @Test
  void delete_deletesProject_forPlatformAdmin() {
    // Given
    when(permissions.isPlatformAdmin(1L)).thenReturn(true);

    // When
    service.delete(1L, 9L);

    // Then
    verify(projects).deleteById(9L);
  }

  @Test
  void delete_throwsAccessDenied_whenActorNotPlatformAdmin() {
    // Given
    when(permissions.isPlatformAdmin(1L)).thenReturn(false);

    // When / Then
    assertThatThrownBy(() -> service.delete(1L, 9L))
        .isInstanceOf(ProjectAccessDeniedException.class);
  }
}
