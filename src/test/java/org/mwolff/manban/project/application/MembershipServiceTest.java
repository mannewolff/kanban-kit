package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.project.domain.ProjectInvitation;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;

/** Verhaltenstests der Mitgliederverwaltung (Mockito an den Ports). */
// PMD.TooManyMethods: umfassende Unit-Suite (Einladen/Annehmen/Rolle/Entfernen/Eigentümer-Transfer,
// je Erfolgs- und Fehlerpfad). Viele kleine @Test-Methoden sind hier gewollt, kein God-Class-Smell.
@SuppressWarnings("PMD.TooManyMethods")
class MembershipServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private ProjectRepository projects;
  private ProjectMembershipRepository memberships;
  private ProjectInvitationRepository invitations;
  private PermissionChecker permissions;
  private InvitationMailer mailer;
  private AppUserRepository users;
  private MembershipService service;

  private static AppUser user(long id, String email) {
    return new AppUser(id, email, "hash", "U" + id, true, PlatformRole.USER);
  }

  private static ProjectInvitation invitation(Instant expiresAt, Instant acceptedAt) {
    return new ProjectInvitation(
        4L, 9L, "guest@x.de", ProjectRole.MEMBER, "hash", expiresAt, acceptedAt, 1L);
  }

  private static ProjectMembership membership(long userId, ProjectRole role) {
    return new ProjectMembership(3L, 9L, userId, role, FIXED);
  }

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
  void listMembers_returnsMembers_forMember() {
    // Given
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.MEMBER)));
    when(memberships.findByProjectId(9L)).thenReturn(List.of(membership(2L, ProjectRole.MEMBER)));
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
    // Given
    when(memberships.findByProjectIdAndUserId(9L, 2L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.listMembers(2L, 9L))
        .isInstanceOf(ProjectNotFoundException.class);
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
    // Given
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.OWNER)));
    when(memberships.findByProjectId(9L)).thenReturn(List.of(membership(2L, ProjectRole.OWNER)));

    // When / Then
    assertThatThrownBy(() -> service.changeRole(1L, 9L, 2L, ProjectRole.MEMBER))
        .isInstanceOf(LastOwnerException.class);
  }

  @Test
  void changeRole_demotesOwner_whenAnotherOwnerRemains() {
    // Given: mehrere OWNER (neben einem MEMBER) -> Degradierung erlaubt, Filter trifft auch
    // Nicht-OWNER
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.OWNER)));
    when(memberships.findByProjectId(9L))
        .thenReturn(
            List.of(
                membership(2L, ProjectRole.OWNER),
                membership(5L, ProjectRole.OWNER),
                membership(7L, ProjectRole.MEMBER)));
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
    when(memberships.findByProjectId(9L)).thenReturn(List.of(membership(5L, ProjectRole.OWNER)));
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
  void changeRole_keepsOwner_whenNewRoleAlsoOwner() {
    // Given: OWNER bleibt OWNER -> Aussperr-Schutz greift nicht (zweite Bedingung false)
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.OWNER)));
    when(memberships.save(any(ProjectMembership.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    ArgumentCaptor<ProjectMembership> captor = ArgumentCaptor.forClass(ProjectMembership.class);
    service.changeRole(1L, 9L, 2L, ProjectRole.OWNER);

    // Then
    verify(memberships).save(captor.capture());
    assertThat(captor.getValue().role()).isEqualTo(ProjectRole.OWNER);
  }

  @Test
  void changeRole_appliesLastOwnerGuardOnlyWhenTargetIsOwner() {
    // Given: das Ziel ist MEMBER (erste Guard-Bedingung false) — selbst wenn die Owner-Liste es
    // (inkonsistent) als einzigen Owner führte, darf der Aussperr-Schutz NICHT greifen. Sichert,
    // dass die „target ist OWNER"-Bedingung tatsächlich geprüft wird.
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.MEMBER)));
    when(memberships.findByProjectId(9L)).thenReturn(List.of(membership(2L, ProjectRole.OWNER)));
    when(memberships.save(any(ProjectMembership.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    ArgumentCaptor<ProjectMembership> captor = ArgumentCaptor.forClass(ProjectMembership.class);
    service.changeRole(1L, 9L, 2L, ProjectRole.MEMBER);

    // Then
    verify(memberships).save(captor.capture());
    assertThat(captor.getValue().role()).isEqualTo(ProjectRole.MEMBER);
  }

  @Test
  void changeRole_keepsSoleOwner_whenNewRoleAlsoOwner() {
    // Given: einziger OWNER bleibt OWNER -> zweite Guard-Bedingung (neue Rolle != OWNER) ist
    // false; der Aussperr-Schutz darf trotz „letzter Owner" nicht greifen.
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.OWNER)));
    when(memberships.findByProjectId(9L)).thenReturn(List.of(membership(2L, ProjectRole.OWNER)));
    when(memberships.save(any(ProjectMembership.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    ArgumentCaptor<ProjectMembership> captor = ArgumentCaptor.forClass(ProjectMembership.class);
    service.changeRole(1L, 9L, 2L, ProjectRole.OWNER);

    // Then
    verify(memberships).save(captor.capture());
    assertThat(captor.getValue().role()).isEqualTo(ProjectRole.OWNER);
  }

  @Test
  void changeRole_throwsLastOwner_whenSoleOwnerAmongOtherMembers() {
    // Given: genau EIN OWNER neben Nicht-OWNER-Mitgliedern. isLastOwner darf nur OWNER zählen —
    // würden alle Mitglieder gezählt (Mutant am Filter-Prädikat), wäre size != 1 und die
    // Degradierung liefe fälschlich durch.
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.OWNER)));
    when(memberships.findByProjectId(9L))
        .thenReturn(List.of(membership(2L, ProjectRole.OWNER), membership(5L, ProjectRole.MEMBER)));

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
    when(memberships.findByProjectId(9L))
        .thenReturn(List.of(membership(2L, ProjectRole.OWNER), membership(5L, ProjectRole.OWNER)));

    // When
    service.removeMember(1L, 9L, 2L);

    // Then
    verify(memberships).deleteById(3L);
  }

  @Test
  void removeMember_appliesLastOwnerGuardOnlyWhenTargetIsOwner() {
    // Given: das Ziel ist MEMBER (erste Guard-Bedingung false) — der Aussperr-Schutz darf nicht
    // greifen, selbst wenn die Owner-Liste es (inkonsistent) als einzigen Owner führte.
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.MEMBER)));
    when(memberships.findByProjectId(9L)).thenReturn(List.of(membership(2L, ProjectRole.OWNER)));

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
    when(memberships.findByProjectId(9L)).thenReturn(List.of(membership(2L, ProjectRole.OWNER)));

    // When / Then
    assertThatThrownBy(() -> service.removeMember(1L, 9L, 2L))
        .isInstanceOf(LastOwnerException.class);
  }

  @Test
  void transferOwnership_makesTargetOwnerAndDemotesCaller() {
    // Given
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.MEMBER)));
    when(memberships.findByProjectIdAndUserId(9L, 1L))
        .thenReturn(Optional.of(membership(1L, ProjectRole.OWNER)));

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
    // Given
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.OWNER)));

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

    // When
    ArgumentCaptor<ProjectMembership> captor = ArgumentCaptor.forClass(ProjectMembership.class);
    service.transferOwnership(1L, 9L, 2L);

    // Then
    verify(memberships, times(1)).save(captor.capture());
    assertThat(captor.getValue().userId()).isEqualTo(2L);
    assertThat(captor.getValue().role()).isEqualTo(ProjectRole.OWNER);
  }

  @Test
  void changeMemberDisplayName_trimsPersistsGlobalNameAndReturnsView() {
    // Given
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.MEMBER)));
    when(users.findById(2L)).thenReturn(Optional.of(user(2, "guest@x.de")));
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    MembershipService.MemberView view =
        service.changeMemberDisplayName(1L, 9L, 2L, "  Neuer Name  ");

    // Then
    assertThat(view.displayName()).isEqualTo("Neuer Name");
    assertThat(view.email()).isEqualTo("guest@x.de");
    assertThat(view.role()).isEqualTo(ProjectRole.MEMBER);
    verify(permissions).require(1L, 9L, Permission.MEMBER_REMOVE);
    ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().displayName()).isEqualTo("Neuer Name");
  }

  @Test
  void changeMemberDisplayName_throwsMemberNotFound_whenMembershipMissing() {
    when(memberships.findByProjectIdAndUserId(9L, 2L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.changeMemberDisplayName(1L, 9L, 2L, "X"))
        .isInstanceOf(MemberNotFoundException.class);
  }

  @Test
  void changeMemberDisplayName_throwsMemberNotFound_whenUserMissing() {
    when(memberships.findByProjectIdAndUserId(9L, 2L))
        .thenReturn(Optional.of(membership(2L, ProjectRole.MEMBER)));
    when(users.findById(2L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.changeMemberDisplayName(1L, 9L, 2L, "X"))
        .isInstanceOf(MemberNotFoundException.class);
  }
}
