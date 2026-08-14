package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.application.UserDisplayNameWriter;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.auth.application.UserSummary;
import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.project.domain.Project;
import org.mwolff.manban.project.domain.ProjectInvitation;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;

/** Verhaltenstests der Mitgliederverwaltung (Mockito an den Ports). */
// PMD.TooManyMethods: umfassende Unit-Suite (Einladen/Annehmen/Rolle/Entfernen/Eigentümer-Transfer,
// je Erfolgs- und Fehlerpfad). Viele kleine @Test-Methoden sind hier gewollt, kein God-Class-Smell.
// PMD.CouplingBetweenObjects: Die Klasse testet einen Service mit zehn Konstruktor-Ports; jeder
// gestubbte Port und jeder Domänentyp in einem Stub zählt mit (21 statt 20). Das misst hier die
// Breite des getesteten Dienstes, nicht ein Design-Problem des Tests.
@SuppressWarnings({"PMD.TooManyMethods", "PMD.CouplingBetweenObjects"})
class MembershipServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private ProjectRepository projects;
  private ProjectMembershipRepository memberships;
  private ProjectInvitationRepository invitations;
  private PermissionChecker permissions;
  private InvitationMailer mailer;
  private UserLookup users;
  private UserDisplayNameWriter displayNames;
  private MembershipService service;

  private static UserSummary user(long id, String email) {
    return new UserSummary(id, email, "U" + id, true);
  }

  private static ProjectInvitation invitation(Instant expiresAt, Instant acceptedAt) {
    return new ProjectInvitation(
        4L, 9L, "guest@x.de", ProjectRole.MEMBER, "hash", expiresAt, acceptedAt, 1L);
  }

  private static ProjectMembership membership(long userId, ProjectRole role) {
    return new ProjectMembership(3L, 9L, userId, role, FIXED);
  }

  private static Project project() {
    return new Project(9L, "P", 1L, FIXED);
  }

  @BeforeEach
  void setUp() {
    projects = mock(ProjectRepository.class);
    memberships = mock(ProjectMembershipRepository.class);
    invitations = mock(ProjectInvitationRepository.class);
    permissions = mock(PermissionChecker.class);
    mailer = mock(InvitationMailer.class);
    users = mock(UserLookup.class);
    displayNames = mock(UserDisplayNameWriter.class);
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
            displayNames,
            authProperties,
            projectProperties,
            clock);
  }

  @Test
  void accept_createsMembership_forMatchingUser() {
    // Given
    when(invitations.findByTokenHash(anyString()))
        .thenReturn(Optional.of(invitation(FIXED.plusSeconds(3600), null)));
    when(users.findById(2L)).thenReturn(Optional.of(user(2, "guest@x.de")));
    when(memberships.findByProjectIdAndUserId(9L, 2L)).thenReturn(Optional.empty());
    when(memberships.save(any(ProjectMembership.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    ArgumentCaptor<ProjectMembership> captor = ArgumentCaptor.forClass(ProjectMembership.class);
    MembershipService.MemberView view = service.accept(2L, "plaintext");

    // Then
    verify(memberships).save(captor.capture());
    assertThat(captor.getValue().role()).isEqualTo(ProjectRole.MEMBER);
    // Rückgabe ist die Sicht auf die (neue) Mitgliedschaft — nicht null.
    assertThat(view.email()).isEqualTo("guest@x.de");
  }

  @Test
  void accept_reusesExistingMembership_whenAlreadyMember() {
    // Given
    when(invitations.findByTokenHash(anyString()))
        .thenReturn(Optional.of(invitation(FIXED.plusSeconds(3600), null)));
    when(users.findById(2L)).thenReturn(Optional.of(user(2, "guest@x.de")));
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.ADMIN)));

    // When
    service.accept(2L, "plaintext");

    // Then
    verify(memberships, never()).save(any(ProjectMembership.class));
  }

  @Test
  void accept_marksInvitationAccepted() {
    // Given
    when(invitations.findByTokenHash(anyString()))
        .thenReturn(Optional.of(invitation(FIXED.plusSeconds(3600), null)));
    when(users.findById(2L)).thenReturn(Optional.of(user(2, "guest@x.de")));
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.MEMBER)));

    // When
    ArgumentCaptor<ProjectInvitation> captor = ArgumentCaptor.forClass(ProjectInvitation.class);
    service.accept(2L, "plaintext");

    // Then
    verify(invitations).save(captor.capture());
    assertThat(captor.getValue().acceptedAt()).isEqualTo(FIXED);
  }

  @Test
  void accept_throwsInvalidInvitation_whenTokenUnknown() {
    // Given
    when(invitations.findByTokenHash(anyString())).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.accept(2L, "plaintext"))
        .isInstanceOf(InvalidInvitationException.class);
  }

  @Test
  void accept_throwsInvalidInvitation_whenAlreadyAccepted() {
    // Given: Downstream (Nutzer + Mitgliedschaft) gestubbt, damit ein Umgehen des
    // Gültigkeits-Guards (Mutant) in einen Erfolg statt in eine spätere Ausnahme umschlägt.
    when(invitations.findByTokenHash(anyString()))
        .thenReturn(Optional.of(invitation(FIXED.plusSeconds(3600), FIXED.minusSeconds(10))));
    when(users.findById(2L)).thenReturn(Optional.of(user(2, "guest@x.de")));
    when(memberships.findByProjectIdAndUserId(9L, 2L)).thenReturn(Optional.empty());
    when(memberships.save(any(ProjectMembership.class))).thenAnswer(inv -> inv.getArgument(0));

    // When / Then
    assertThatThrownBy(() -> service.accept(2L, "plaintext"))
        .isInstanceOf(InvalidInvitationException.class);
  }

  @Test
  void accept_throwsInvalidInvitation_whenExpired() {
    // Given: Downstream gestubbt (s. o.).
    when(invitations.findByTokenHash(anyString()))
        .thenReturn(Optional.of(invitation(FIXED.minusSeconds(1), null)));
    when(users.findById(2L)).thenReturn(Optional.of(user(2, "guest@x.de")));
    when(memberships.findByProjectIdAndUserId(9L, 2L)).thenReturn(Optional.empty());
    when(memberships.save(any(ProjectMembership.class))).thenAnswer(inv -> inv.getArgument(0));

    // When / Then
    assertThatThrownBy(() -> service.accept(2L, "plaintext"))
        .isInstanceOf(InvalidInvitationException.class);
  }

  @Test
  void accept_throwsInvalidInvitation_whenUserUnknown() {
    // Given
    when(invitations.findByTokenHash(anyString()))
        .thenReturn(Optional.of(invitation(FIXED.plusSeconds(3600), null)));
    when(users.findById(2L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.accept(2L, "plaintext"))
        .isInstanceOf(InvalidInvitationException.class);
  }

  @Test
  void accept_throwsEmailMismatch_whenUserEmailDiffers() {
    // Given
    when(invitations.findByTokenHash(anyString()))
        .thenReturn(Optional.of(invitation(FIXED.plusSeconds(3600), null)));
    when(users.findById(2L)).thenReturn(Optional.of(user(2, "other@x.de")));

    // When / Then
    assertThatThrownBy(() -> service.accept(2L, "plaintext"))
        .isInstanceOf(InvitationEmailMismatchException.class);
  }

  @Test
  void listMembers_resolvesAccessViaPermissionChecker() {
    // Given — ein Plattform-Admin ohne echte Mitgliedschaft passiert requireMembership mit einer
    // synthetischen OWNER-Rolle; der Service darf nicht selbst auf eine reale Mitgliedschaft
    // prüfen.
    when(permissions.requireMembership(7L, 9L))
        .thenReturn(new ProjectMembership(null, 9L, 7L, ProjectRole.OWNER, FIXED));
    when(projects.findById(9L)).thenReturn(Optional.of(project()));
    when(memberships.findByProjectId(9L)).thenReturn(List.of(membership(2L, ProjectRole.MEMBER)));
    when(users.findById(2L)).thenReturn(Optional.of(user(2, "guest@x.de")));

    // When
    List<MembershipService.MemberView> result = service.listMembers(7L, 9L);

    // Then
    verify(permissions).requireMembership(7L, 9L);
    assertThat(result)
        .singleElement()
        .extracting(MembershipService.MemberView::email)
        .isEqualTo("guest@x.de");
  }

  /** Jedes Mitglied darf die Liste sehen — auch der nur lesende VIEWER. */
  @ParameterizedTest
  @EnumSource(ProjectRole.class)
  void listMembers_returnsMembers_forEveryProjectRole(ProjectRole role) {
    // Given
    when(permissions.requireMembership(2L, 9L)).thenReturn(membership(2L, role));
    when(projects.findById(9L)).thenReturn(Optional.of(project()));
    when(memberships.findByProjectId(9L)).thenReturn(List.of(membership(2L, role)));
    when(users.findById(2L)).thenReturn(Optional.of(user(2, "guest@x.de")));

    // When
    List<MembershipService.MemberView> result = service.listMembers(2L, 9L);

    // Then
    assertThat(result)
        .singleElement()
        .extracting(MembershipService.MemberView::email)
        .isEqualTo("guest@x.de");
  }

  @Test
  void listMembers_throwsProjectNotFound_forNonMember() {
    // Given — PlatformRole.USER ohne Mitgliedschaft: der PermissionChecker wirft.
    when(permissions.requireMembership(2L, 9L)).thenThrow(new ProjectNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.listMembers(2L, 9L))
        .isInstanceOf(ProjectNotFoundException.class);
    verify(memberships, never()).findByProjectId(anyLong());
  }

  @Test
  void listMembers_throwsProjectNotFound_forPlatformAdminOnUnknownProject() {
    // Given — requireMembership erzeugt dem Admin eine synthetische Mitgliedschaft, ohne die
    // Existenz des Projekts zu prüfen. Eine unbekannte projectId bleibt trotzdem 404, nie 200 [].
    when(permissions.requireMembership(7L, 404L))
        .thenReturn(new ProjectMembership(null, 404L, 7L, ProjectRole.OWNER, FIXED));
    when(projects.findById(404L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.listMembers(7L, 404L))
        .isInstanceOf(ProjectNotFoundException.class);
    verify(memberships, never()).findByProjectId(anyLong());
  }

  @Test
  void changeRole_persistsNewRole() {
    // Given
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.MEMBER)));
    when(memberships.save(any(ProjectMembership.class))).thenAnswer(inv -> inv.getArgument(0));
    when(users.findById(2L)).thenReturn(Optional.of(user(2, "guest@x.de")));

    // When
    ArgumentCaptor<ProjectMembership> captor = ArgumentCaptor.forClass(ProjectMembership.class);
    MembershipService.MemberView view = service.changeRole(1L, 9L, 2L, ProjectRole.ADMIN);

    // Then
    verify(memberships).save(captor.capture());
    assertThat(captor.getValue().role()).isEqualTo(ProjectRole.ADMIN);
    // Rückgabe ist die Sicht auf das aktualisierte Mitglied (aus der Nutzer-Fundstelle) — nicht
    // null.
    assertThat(view.email()).isEqualTo("guest@x.de");
  }

  @Test
  void changeRole_throwsMemberNotFound_whenTargetMissing() {
    // Given
    when(memberships.findByProjectIdAndUserId(9L, 2L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.changeRole(1L, 9L, 2L, ProjectRole.ADMIN))
        .isInstanceOf(MemberNotFoundException.class);
  }

  @Test
  void changeRole_throwsLastOwner_whenDemotingSoleOwner() {
    // Given: die gesperrte Owner-Menge besteht nur aus dem Ziel selbst.
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.OWNER)));
    when(memberships.lockOwnerUserIds(9L)).thenReturn(List.of(2L));

    // When / Then
    assertThatThrownBy(() -> service.changeRole(1L, 9L, 2L, ProjectRole.MEMBER))
        .isInstanceOf(LastOwnerException.class);
  }

  @Test
  void changeRole_demotesOwner_whenAnotherOwnerRemains() {
    // Given: mehrere OWNER -> Degradierung erlaubt
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.OWNER)));
    when(memberships.lockOwnerUserIds(9L)).thenReturn(List.of(2L, 5L));
    when(memberships.save(any(ProjectMembership.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    ArgumentCaptor<ProjectMembership> captor = ArgumentCaptor.forClass(ProjectMembership.class);
    service.changeRole(1L, 9L, 2L, ProjectRole.MEMBER);

    // Then
    verify(memberships).save(captor.capture());
    assertThat(captor.getValue().role()).isEqualTo(ProjectRole.MEMBER);
  }

  @Test
  void changeRole_demotesOwner_whenSoleOwnerIsDifferentUser() {
    // Given: genau ein OWNER, aber nicht das Ziel -> Ziel ist nicht der letzte OWNER
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.OWNER)));
    when(memberships.lockOwnerUserIds(9L)).thenReturn(List.of(5L));
    when(memberships.save(any(ProjectMembership.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    ArgumentCaptor<ProjectMembership> captor = ArgumentCaptor.forClass(ProjectMembership.class);
    MembershipService.MemberView view = service.changeRole(1L, 9L, 2L, ProjectRole.MEMBER);

    // Then
    verify(memberships).save(captor.capture());
    assertThat(captor.getValue().role()).isEqualTo(ProjectRole.MEMBER);
    // Nutzer ist hier NICHT nachschlagbar -> Fallback-Sicht mit userId, aber ohne E-Mail (nicht
    // null). Deckt den orElseGet-Zweig von toView ab.
    assertThat(view.userId()).isEqualTo(2L);
  }

  @Test
  void changeRole_keepsSoleOwner_whenNewRoleAlsoOwner() {
    // Given: der EINZIGE OWNER bleibt OWNER -> der Aussperr-Schutz darf nicht greifen, denn die
    // Owner-Menge schrumpft nicht.
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.OWNER)));
    when(memberships.lockOwnerUserIds(9L)).thenReturn(List.of(2L));
    when(memberships.save(any(ProjectMembership.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    ArgumentCaptor<ProjectMembership> captor = ArgumentCaptor.forClass(ProjectMembership.class);
    service.changeRole(1L, 9L, 2L, ProjectRole.OWNER);

    // Then
    verify(memberships).save(captor.capture());
    assertThat(captor.getValue().role()).isEqualTo(ProjectRole.OWNER);
  }

  @Test
  void changeRole_trustsLockedOwnerSet_notTheRoleOnTheLoadedMembership() {
    // Given: die geladene Mitgliedschaft trägt noch MEMBER, die gesperrte Owner-Menge führt den
    // Benutzer aber als einzigen OWNER — die Lage, wenn eine andere Transaktion ihn soeben
    // befördert hat. Maßgeblich ist die gesperrte Menge, sonst liefe die Degradierung durch und
    // das Projekt bliebe ownerlos (Issue #498).
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.MEMBER)));
    when(memberships.lockOwnerUserIds(9L)).thenReturn(List.of(2L));

    // When / Then
    assertThatThrownBy(() -> service.changeRole(1L, 9L, 2L, ProjectRole.MEMBER))
        .isInstanceOf(LastOwnerException.class);
  }

  @Test
  void removeMember_deletesMembership() {
    // Given
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.MEMBER)));

    // When
    service.removeMember(1L, 9L, 2L);

    // Then
    verify(memberships).deleteById(3L);
  }

  @Test
  void removeMember_removesOwner_whenAnotherOwnerRemains() {
    // Given: OWNER, aber nicht der letzte -> Entfernen erlaubt
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.OWNER)));
    when(memberships.lockOwnerUserIds(9L)).thenReturn(List.of(2L, 5L));

    // When
    service.removeMember(1L, 9L, 2L);

    // Then
    verify(memberships).deleteById(3L);
  }

  @Test
  void removeMember_removesMember_whenSoleOwnerIsSomeoneElse() {
    // Given: es gibt genau einen OWNER, aber das ist nicht das Ziel -> Entfernen erlaubt.
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.MEMBER)));
    when(memberships.lockOwnerUserIds(9L)).thenReturn(List.of(5L));

    // When
    service.removeMember(1L, 9L, 2L);

    // Then
    verify(memberships).deleteById(3L);
  }

  @Test
  void removeMember_throwsMemberNotFound_whenTargetMissing() {
    // Given
    when(memberships.findByProjectIdAndUserId(9L, 2L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.removeMember(1L, 9L, 2L))
        .isInstanceOf(MemberNotFoundException.class);
  }

  @Test
  void removeMember_throwsLastOwner_whenRemovingSoleOwner() {
    // Given
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.OWNER)));
    when(memberships.lockOwnerUserIds(9L)).thenReturn(List.of(2L));

    // When / Then
    assertThatThrownBy(() -> service.removeMember(1L, 9L, 2L))
        .isInstanceOf(LastOwnerException.class);
  }

  @Test
  void transferOwnership_makesTargetOwnerAndDemotesCaller() {
    // Given: der Aufrufer ist der EINZIGE Owner. Der Transfer ist trotzdem zulässig, weil er
    // bestandserhaltend ist — das Ziel wird Owner, während der Aufrufer es aufgibt (Issue #498).
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.MEMBER)));
    when(memberships.findByProjectIdAndUserId(9L, 1L))
        .thenReturn(Optional.of(membership(1L, ProjectRole.OWNER)));
    when(memberships.lockOwnerUserIds(9L)).thenReturn(List.of(1L));

    // When
    ArgumentCaptor<ProjectMembership> captor = ArgumentCaptor.forClass(ProjectMembership.class);
    service.transferOwnership(1L, 9L, 2L);

    // Then
    verify(memberships, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .anySatisfy(
            m -> {
              assertThat(m.userId()).isEqualTo(2L);
              assertThat(m.role()).isEqualTo(ProjectRole.OWNER);
            })
        .anySatisfy(
            m -> {
              assertThat(m.userId()).isEqualTo(1L);
              assertThat(m.role()).isEqualTo(ProjectRole.ADMIN);
            });
  }

  @Test
  void transferOwnership_requiresOwnerTransferPermission() {
    // Given
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.MEMBER)));
    when(memberships.findByProjectIdAndUserId(9L, 1L))
        .thenReturn(Optional.of(membership(1L, ProjectRole.OWNER)));
    when(memberships.lockOwnerUserIds(9L)).thenReturn(List.of(1L));

    // When
    service.transferOwnership(1L, 9L, 2L);

    // Then
    verify(permissions).require(1L, 9L, Permission.PROJECT_OWNER_TRANSFER);
  }

  @Test
  void transferOwnership_throwsMemberNotFound_whenTargetNotMember() {
    // Given
    when(memberships.findByProjectIdAndUserId(9L, 2L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.transferOwnership(1L, 9L, 2L))
        .isInstanceOf(MemberNotFoundException.class);
  }

  @Test
  void transferOwnership_isNoOp_whenTargetAlreadyOwner() {
    // Given: das Ziel steht bereits in der gesperrten Owner-Menge.
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.OWNER)));
    when(memberships.lockOwnerUserIds(9L)).thenReturn(List.of(1L, 2L));

    // When
    service.transferOwnership(1L, 9L, 2L);

    // Then
    verify(memberships, never()).save(any(ProjectMembership.class));
  }

  @Test
  void transferOwnership_doesNotDemoteCaller_whenNotOwnerMember() {
    // Given — Aufrufer ist kein OWNER-Mitglied (z. B. Plattform-Admin-Sonderfall): nur das Ziel
    // wird zum OWNER, es wird niemand herabgestuft.
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.MEMBER)));
    when(memberships.findByProjectIdAndUserId(9L, 1L))
        .thenReturn(Optional.of(membership(1L, ProjectRole.ADMIN)));
    when(memberships.lockOwnerUserIds(9L)).thenReturn(List.of(7L));

    // When
    ArgumentCaptor<ProjectMembership> captor = ArgumentCaptor.forClass(ProjectMembership.class);
    service.transferOwnership(1L, 9L, 2L);

    // Then
    verify(memberships, times(1)).save(captor.capture());
    assertThat(captor.getValue().userId()).isEqualTo(2L);
    assertThat(captor.getValue().role()).isEqualTo(ProjectRole.OWNER);
  }

  @Test
  void changeMemberDisplayName_delegatesToWriterAndReturnsView() {
    // Given: das Trimmen liegt seit Issue #460 im auth-Port — hier wird der Rohwert durchgereicht.
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.MEMBER)));
    when(displayNames.updateDisplayName(2L, "  Neuer Name  "))
        .thenReturn(Optional.of(new UserSummary(2L, "guest@x.de", "Neuer Name", true)));

    // When
    MembershipService.MemberView view =
        service.changeMemberDisplayName(1L, 9L, 2L, "  Neuer Name  ");

    // Then
    assertThat(view.userId()).isEqualTo(2L);
    assertThat(view.displayName()).isEqualTo("Neuer Name");
    assertThat(view.email()).isEqualTo("guest@x.de");
    assertThat(view.role()).isEqualTo(ProjectRole.MEMBER);
    verify(permissions).require(1L, 9L, Permission.MEMBER_REMOVE);
    verify(displayNames).updateDisplayName(2L, "  Neuer Name  ");
    // Der Schreib-Port schlägt den Benutzer selbst nach; ein zweiter Lookup hier wäre ein
    // zusätzliches Select ohne Erkenntnisgewinn (#472).
    verify(users, never()).findById(anyLong());
  }

  @Test
  void changeMemberDisplayName_doesNotWrite_whenMemberRemovePermissionMissing() {
    // Verhaltens-Neutralitaet (Issue #460): Der auth-Port prueft bewusst keine Rechte — die
    // Autorisierung bleibt vollstaendig hier. Fehlt MEMBER_REMOVE, wird nichts geschrieben.
    when(permissions.require(1L, 9L, Permission.MEMBER_REMOVE))
        .thenThrow(new ProjectAccessDeniedException());

    assertThatThrownBy(() -> service.changeMemberDisplayName(1L, 9L, 2L, "Neuer Name"))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(displayNames, never()).updateDisplayName(anyLong(), anyString());
  }

  @Test
  void changeMemberDisplayName_throwsMemberNotFound_whenMembershipMissing() {
    when(memberships.findByProjectIdAndUserId(9L, 2L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.changeMemberDisplayName(1L, 9L, 2L, "X"))
        .isInstanceOf(MemberNotFoundException.class);
  }

  @Test
  void changeMemberDisplayName_throwsMemberNotFound_whenUserMissing() {
    // Der Schreib-Port meldet den unbekannten Benutzer als leeres Optional; nach außen bleibt es
    // ein fehlendes Mitglied (404) — kein auth-Vokabular im project-Modul (#472).
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.MEMBER)));
    when(displayNames.updateDisplayName(2L, "X")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.changeMemberDisplayName(1L, 9L, 2L, "X"))
        .isInstanceOf(MemberNotFoundException.class);
  }
}
