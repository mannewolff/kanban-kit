package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.auth.application.UserSummary;
import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.project.domain.Project;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.context.ApplicationEventPublisher;

/** Verhaltenstests der Projekt-Use-Cases (Mockito an den Ports). */
class ProjectServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private ProjectRepository projects;
  private ProjectMembershipRepository memberships;
  private PermissionChecker permissions;
  private UserLookup users;
  private InvitationMailer mailer;
  private ApplicationEventPublisher events;
  private ProjectService service;
  private AuthProperties authProperties;
  private Clock clock;

  private static ProjectMembership membership(long projectId, long userId, ProjectRole role) {
    return new ProjectMembership(1L, projectId, userId, role, FIXED);
  }

  @BeforeEach
  void setUp() {
    projects = mock(ProjectRepository.class);
    memberships = mock(ProjectMembershipRepository.class);
    permissions = mock(PermissionChecker.class);
    users = mock(UserLookup.class);
    mailer = mock(InvitationMailer.class);
    events = mock(ApplicationEventPublisher.class);
    authProperties = new AuthProperties("https://app.example", null, null, null, null);
    clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    service =
        new ProjectService(
            projects, memberships, permissions, users, mailer, events, authProperties, clock);
  }

  @Test
  void create_setsCreatedAtFromInjectedClock() {
    // Given
    when(permissions.isPlatformAdmin(1L)).thenReturn(true);
    when(users.findByEmail("owner@x.de"))
        .thenReturn(Optional.of(new UserSummary(2L, "owner@x.de", "Owner", true)));
    when(projects.save(any(Project.class)))
        .thenAnswer(
            inv -> {
              Project p = inv.getArgument(0);
              return p.id() == null
                  ? new Project(
                      9L,
                      p.name(),
                      p.ownerUserId(),
                      p.createdAt(),
                      p.interactiveUsageSince(),
                      p.dashboardParticipation(),
                      p.monthlyBudgetUsd())
                  : p;
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
                  p.dashboardParticipation(),
                  p.monthlyBudgetUsd());
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
                  p.dashboardParticipation(),
                  p.monthlyBudgetUsd());
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
                  p.dashboardParticipation(),
                  p.monthlyBudgetUsd());
            });

    // When
    service.create(1L, "Neu", "owner@x.de");

    // Then
    verify(mailer)
        .sendProjectAssignedEmail(eq("owner@x.de"), eq("Neu"), eq(ProjectRole.OWNER), anyString());
  }

  @Test
  void create_publishesProjectCreatedEvent_withProjectIdAndOwnerId() {
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
                  p.dashboardParticipation(),
                  p.monthlyBudgetUsd());
            });

    // When
    ArgumentCaptor<ProjectCreatedEvent> captor = ArgumentCaptor.forClass(ProjectCreatedEvent.class);
    service.create(1L, "Neu", "owner@x.de");

    // Then
    verify(events).publishEvent(captor.capture());
    assertThat(captor.getValue().projectId()).isEqualTo(9L);
    assertThat(captor.getValue().ownerUserId()).isEqualTo(2L);
  }

  @Test
  void create_publishesEventBeforeSendingAssignmentMail() {
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
                  p.dashboardParticipation(),
                  p.monthlyBudgetUsd());
            });

    // When
    service.create(1L, "Neu", "owner@x.de");

    // Then: Board-Anlage (Event) vor dem Mailversand — scheitert sie, rollt alles atomar zurueck.
    InOrder order = inOrder(events, mailer);
    order.verify(events).publishEvent(any(ProjectCreatedEvent.class));
    order
        .verify(mailer)
        .sendProjectAssignedEmail(anyString(), anyString(), any(ProjectRole.class), anyString());
  }

  @Test
  void create_normalizesOwnerEmailToLowercase() {
    // Given: gespeicherte E-Mail ist lowercase; der Aufruf liefert sie gemischt/mit Rand.
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
                  p.dashboardParticipation(),
                  p.monthlyBudgetUsd());
            });

    // When
    service.create(1L, "Neu", "  Owner@X.de ");

    // Then
    verify(users).findByEmail("owner@x.de");
  }

  @Test
  void create_throwsMemberNotApproved_whenOwnerPending() {
    // Given: Owner existiert, ist aber noch nicht freigegeben.
    when(permissions.isPlatformAdmin(1L)).thenReturn(true);
    when(users.findByEmail("owner@x.de"))
        .thenReturn(Optional.of(new UserSummary(2L, "owner@x.de", "Owner", false)));

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
    when(projects.findAll())
        .thenReturn(List.of(new Project(9L, "P", 2L, FIXED, null, false, null)));

    // When
    List<ProjectService.ProjectView> result = service.list(1L);

    // Then
    assertThat(result)
        .singleElement()
        .extracting(ProjectService.ProjectView::role)
        .isEqualTo(ProjectRole.OWNER);
  }

  /** Die Projektliste trägt die zuletzt vom Kit gemeldete Strenge-Stufe (Issue #2014). */
  @Test
  void list_traegtDieGemeldeteStrengeStufe() {
    when(permissions.isPlatformAdmin(1L)).thenReturn(true);
    when(memberships.findByUserId(1L)).thenReturn(List.of());
    when(projects.findAll())
        .thenReturn(
            List.of(
                new Project(9L, "P", 2L, FIXED, null, false, null, "strict"),
                new Project(10L, "Q", 2L, FIXED, null, false, null)));

    assertThat(service.list(1L))
        .extracting(ProjectService.ProjectView::strictness)
        .containsExactly("strict", null);
  }

  @Test
  void list_returnsOnlyMemberProjects_forRegularUser() {
    // Given
    when(permissions.isPlatformAdmin(2L)).thenReturn(false);
    when(memberships.findByUserId(2L)).thenReturn(List.of(membership(9L, 2L, ProjectRole.MEMBER)));
    when(projects.findById(9L))
        .thenReturn(Optional.of(new Project(9L, "P", 1L, FIXED, null, false, null)));

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
  void listAccessible_returnsAllProjects_forPlatformAdmin() {
    // Given: derselbe Sichtbarkeitsschnitt wie list — der Admin sieht alles (#489).
    when(permissions.isPlatformAdmin(1L)).thenReturn(true);
    when(projects.findAll())
        .thenReturn(List.of(new Project(9L, "P", 2L, FIXED, null, false, null)));

    // When / Then
    assertThat(service.listAccessible(1L))
        .containsExactly(new ProjectService.AccessibleProject(9L, "P"));
  }

  @Test
  void listAccessible_returnsOnlyMemberProjects_forRegularUser() {
    // Given
    when(permissions.isPlatformAdmin(2L)).thenReturn(false);
    when(memberships.findByUserId(2L)).thenReturn(List.of(membership(9L, 2L, ProjectRole.MEMBER)));
    when(projects.findById(9L))
        .thenReturn(Optional.of(new Project(9L, "P", 1L, FIXED, null, false, null)));

    // When / Then
    assertThat(service.listAccessible(2L))
        .containsExactly(new ProjectService.AccessibleProject(9L, "P"));
  }

  @Test
  void listAccessible_skipsProjectsThatCannotBeLoaded_forRegularUser() {
    // Given
    when(permissions.isPlatformAdmin(2L)).thenReturn(false);
    when(memberships.findByUserId(2L)).thenReturn(List.of(membership(9L, 2L, ProjectRole.MEMBER)));
    when(projects.findById(9L)).thenReturn(Optional.empty());

    // When / Then
    assertThat(service.listAccessible(2L)).isEmpty();
  }

  @Test
  void rename_trimsAndPersistsName() {
    // Given
    when(permissions.require(2L, 9L, Permission.PROJECT_EDIT))
        .thenReturn(membership(9L, 2L, ProjectRole.OWNER));
    when(projects.findById(9L))
        .thenReturn(Optional.of(new Project(9L, "Alt", 2L, FIXED, null, false, null)));
    when(projects.save(any(Project.class)))
        .thenAnswer(
            inv -> {
              Project p = inv.getArgument(0);
              return p.id() == null
                  ? new Project(
                      9L,
                      p.name(),
                      p.ownerUserId(),
                      p.createdAt(),
                      p.interactiveUsageSince(),
                      p.dashboardParticipation(),
                      p.monthlyBudgetUsd())
                  : p;
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
    when(projects.findById(9L))
        .thenReturn(Optional.of(new Project(9L, "Alt", 2L, FIXED, null, false, null)));
    when(projects.save(any(Project.class)))
        .thenAnswer(
            inv -> {
              Project p = inv.getArgument(0);
              return p.id() == null
                  ? new Project(
                      9L,
                      p.name(),
                      p.ownerUserId(),
                      p.createdAt(),
                      p.interactiveUsageSince(),
                      p.dashboardParticipation(),
                      p.monthlyBudgetUsd())
                  : p;
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

  // --- Monatsbudget festlegen und entfernen (Issue #1965, Plan #1962 E3/E4) -----------------
  // Mit der echten PermissionChecker über den gemockten Mitgliedschaften: Erst so belegt der Test,
  // dass requireRealRole und nicht requireOwner prüft — ein Plattform-Admin passiert hier nicht.

  /** Der echte Owner schreibt gültige Beträge, auch an den Grenzen 0,01 und 9 999 999 999,99. */
  @Test
  void setMonthlyBudget_schreibtGueltigeBetraege_fuerDenEchtenOwner() {
    // Given
    ProjectService echt = mitEchterRechtepruefung(false, ProjectRole.OWNER);

    // When
    echt.setMonthlyBudget(2L, 9L, new BigDecimal("100.00"));
    echt.setMonthlyBudget(2L, 9L, new BigDecimal("0.01"));
    echt.setMonthlyBudget(2L, 9L, new BigDecimal("9999999999.99"));
    echt.setMonthlyBudget(2L, 9L, new BigDecimal("5"));

    // Then
    verify(projects).setMonthlyBudget(9L, new BigDecimal("100.00"));
    verify(projects).setMonthlyBudget(9L, new BigDecimal("0.01"));
    verify(projects).setMonthlyBudget(9L, new BigDecimal("9999999999.99"));
    verify(projects).setMonthlyBudget(9L, new BigDecimal("5"));
  }

  /**
   * Je ein Fall für 0, einen negativen Wert, drei Nachkommastellen und 10 000 000 000,00 (E3):
   * abgewiesen, und am Repository wird nicht geschrieben — das bisherige Budget bleibt.
   */
  @ParameterizedTest
  @ValueSource(strings = {"0", "-5.00", "1.005", "10000000000.00"})
  void setMonthlyBudget_weistUngueltigeBetraegeAb_undSchreibtNicht(String betrag) {
    // Given
    ProjectService echt = mitEchterRechtepruefung(false, ProjectRole.OWNER);

    // When / Then
    assertThatThrownBy(() -> echt.setMonthlyBudget(2L, 9L, new BigDecimal(betrag)))
        .isInstanceOf(InvalidMonthlyBudgetException.class);
    verify(projects, never()).setMonthlyBudget(eq(9L), any());
  }

  @Test
  void removeMonthlyBudget_schreibtNull_fuerDenEchtenOwner() {
    // Given
    ProjectService echt = mitEchterRechtepruefung(false, ProjectRole.OWNER);

    // When
    echt.removeMonthlyBudget(2L, 9L);

    // Then
    verify(projects).setMonthlyBudget(9L, null);
  }

  /**
   * Die Rolle ADMIN ist als echtes Mitglied kein Owner und bekommt 403, beim Festlegen wie beim
   * Entfernen. Die Rechte gehen vor dem Betrag: Auch eine ungültige 0 ergibt 403, nicht 400.
   */
  @ParameterizedTest
  @EnumSource(
      value = ProjectRole.class,
      names = {"ADMIN", "MEMBER", "VIEWER"})
  void monthlyBudget_verweigertJederRolleAusserOwnerMit403(ProjectRole rolle) {
    // Given
    ProjectService echt = mitEchterRechtepruefung(false, rolle);

    // When / Then
    assertThatThrownBy(() -> echt.setMonthlyBudget(2L, 9L, new BigDecimal("100.00")))
        .isInstanceOf(ProjectAccessDeniedException.class);
    assertThatThrownBy(() -> echt.setMonthlyBudget(2L, 9L, BigDecimal.ZERO))
        .isInstanceOf(ProjectAccessDeniedException.class);
    assertThatThrownBy(() -> echt.removeMonthlyBudget(2L, 9L))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(projects, never()).setMonthlyBudget(eq(9L), any());
  }

  /**
   * Ein Nichtmitglied bekommt 404 — und ebenso ein Plattform-Admin ohne Mitgliedschaft, der sonst
   * jede Rechteprüfung des Projekts passiert.
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void monthlyBudget_verbirgtDasProjektOhneMitgliedschaftMit404(boolean plattformAdmin) {
    // Given
    ProjectService echt = mitEchterRechtepruefung(plattformAdmin, null);

    // When / Then
    assertThatThrownBy(() -> echt.setMonthlyBudget(2L, 9L, new BigDecimal("100.00")))
        .isInstanceOf(ProjectNotFoundException.class);
    assertThatThrownBy(() -> echt.removeMonthlyBudget(2L, 9L))
        .isInstanceOf(ProjectNotFoundException.class);
    verify(projects, never()).setMonthlyBudget(eq(9L), any());
  }

  /**
   * Der Service mit der echten {@link PermissionChecker}: Benutzer 2 ist Mitglied von Projekt 9 in
   * der Rolle {@code rolle} ({@code null}: kein Mitglied) und Plattform-Admin, wenn {@code
   * plattformAdmin}.
   */
  private ProjectService mitEchterRechtepruefung(
      boolean plattformAdmin, @Nullable ProjectRole rolle) {
    PlatformAdminChecker admins = mock(PlatformAdminChecker.class);
    when(admins.isPlatformAdmin(anyLong())).thenReturn(plattformAdmin);
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.ofNullable(rolle).map(r -> membership(9L, 2L, r)));
    PermissionChecker echt =
        new PermissionChecker(
            memberships, mock(RolePermissionRepository.class), admins, projects, clock);
    return new ProjectService(
        projects, memberships, echt, users, mailer, events, authProperties, clock);
  }
}
