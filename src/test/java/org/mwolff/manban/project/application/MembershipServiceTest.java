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
import org.mwolff.manban.project.domain.ProjectInvitation;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;

/** Verhaltenstests der Mitgliederverwaltung (Mockito an den Ports). */
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
        return new ProjectInvitation(4L, 9L, "guest@x.de", ProjectRole.MEMBER, "hash",
                expiresAt, acceptedAt, 1L);
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
        AuthProperties authProperties = new AuthProperties("https://app.example", null, null, null, null, null);
        ProjectProperties projectProperties = new ProjectProperties(Duration.ofDays(7));
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
        service = new MembershipService(projects, memberships, invitations, permissions, mailer, users,
                authProperties, projectProperties, clock);
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
    void accept_createsMembership_forMatchingUser() {
        // Given
        when(invitations.findByTokenHash(anyString()))
                .thenReturn(Optional.of(invitation(FIXED.plusSeconds(3600), null)));
        when(users.findById(2L)).thenReturn(Optional.of(user(2, "guest@x.de")));
        when(memberships.findByProjectIdAndUserId(9L, 2L)).thenReturn(Optional.empty());
        when(memberships.save(any(ProjectMembership.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        ArgumentCaptor<ProjectMembership> captor = ArgumentCaptor.forClass(ProjectMembership.class);
        service.accept(2L, "plaintext");

        // Then
        verify(memberships).save(captor.capture());
        assertThat(captor.getValue().role()).isEqualTo(ProjectRole.MEMBER);
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
        // Given
        when(invitations.findByTokenHash(anyString()))
                .thenReturn(Optional.of(invitation(FIXED.plusSeconds(3600), FIXED.minusSeconds(10))));

        // When / Then
        assertThatThrownBy(() -> service.accept(2L, "plaintext"))
                .isInstanceOf(InvalidInvitationException.class);
    }

    @Test
    void accept_throwsInvalidInvitation_whenExpired() {
        // Given
        when(invitations.findByTokenHash(anyString()))
                .thenReturn(Optional.of(invitation(FIXED.minusSeconds(1), null)));

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
        assertThat(result).singleElement().extracting(MembershipService.MemberView::email).isEqualTo("guest@x.de");
    }

    @Test
    void listMembers_throwsProjectNotFound_forNonMember() {
        // Given
        when(memberships.findByProjectIdAndUserId(9L, 2L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.listMembers(2L, 9L)).isInstanceOf(ProjectNotFoundException.class);
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
        service.changeRole(1L, 9L, 2L, ProjectRole.ADMIN);

        // Then
        verify(memberships).save(captor.capture());
        assertThat(captor.getValue().role()).isEqualTo(ProjectRole.ADMIN);
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
        // Given: mehrere OWNER (neben einem MEMBER) -> Degradierung erlaubt, Filter trifft auch Nicht-OWNER
        when(memberships.findByProjectIdAndUserId(9L, 2L))
                .thenReturn(Optional.of(membership(2L, ProjectRole.OWNER)));
        when(memberships.findByProjectId(9L)).thenReturn(List.of(
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
        service.changeRole(1L, 9L, 2L, ProjectRole.MEMBER);

        // Then
        verify(memberships).save(captor.capture());
        assertThat(captor.getValue().role()).isEqualTo(ProjectRole.MEMBER);
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
        when(memberships.findByProjectId(9L)).thenReturn(List.of(
                membership(2L, ProjectRole.OWNER), membership(5L, ProjectRole.OWNER)));

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
        assertThatThrownBy(() -> service.removeMember(1L, 9L, 2L)).isInstanceOf(LastOwnerException.class);
    }
}
