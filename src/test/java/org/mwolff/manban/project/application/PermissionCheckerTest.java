package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;

/** Verhaltenstests der zentralen Rechteprüfung (Mockito an den Ports). */
class PermissionCheckerTest {

    private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

    private ProjectMembershipRepository memberships;
    private RolePermissionRepository rolePermissions;
    private AppUserRepository users;
    private PermissionChecker checker;

    private static AppUser user(long id, PlatformRole role) {
        return new AppUser(id, "u" + id + "@x.de", "hash", "U", true, role);
    }

    private static ProjectMembership membership(long projectId, long userId, ProjectRole role) {
        return new ProjectMembership(1L, projectId, userId, role, FIXED);
    }

    @BeforeEach
    void setUp() {
        memberships = mock(ProjectMembershipRepository.class);
        rolePermissions = mock(RolePermissionRepository.class);
        users = mock(AppUserRepository.class);
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
        checker = new PermissionChecker(memberships, rolePermissions, users, clock);
    }

    @Test
    void isPlatformAdmin_returnsTrue_forAdminUser() {
        // Given
        when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.ADMIN)));

        // When / Then
        assertThat(checker.isPlatformAdmin(1L)).isTrue();
    }

    @Test
    void isPlatformAdmin_returnsFalse_forUnknownUser() {
        // Given
        when(users.findById(1L)).thenReturn(Optional.empty());

        // When / Then
        assertThat(checker.isPlatformAdmin(1L)).isFalse();
    }

    @Test
    void hasPermission_returnsTrue_forPlatformAdmin_withoutMembership() {
        // Given
        when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.ADMIN)));

        // When / Then
        assertThat(checker.hasPermission(1L, 7L, Permission.TICKET_CREATE)).isTrue();
    }

    @Test
    void hasPermission_returnsTrue_whenMemberRoleGrantsPermission() {
        // Given
        when(users.findById(2L)).thenReturn(Optional.of(user(2, PlatformRole.USER)));
        when(memberships.findByProjectIdAndUserId(7L, 2L))
                .thenReturn(Optional.of(membership(7L, 2L, ProjectRole.MEMBER)));
        when(rolePermissions.isGranted(ProjectRole.MEMBER, Permission.TICKET_CREATE)).thenReturn(true);

        // When / Then
        assertThat(checker.hasPermission(2L, 7L, Permission.TICKET_CREATE)).isTrue();
    }

    @Test
    void hasPermission_returnsFalse_whenMemberRoleLacksPermission() {
        // Given
        when(users.findById(2L)).thenReturn(Optional.of(user(2, PlatformRole.USER)));
        when(memberships.findByProjectIdAndUserId(7L, 2L))
                .thenReturn(Optional.of(membership(7L, 2L, ProjectRole.VIEWER)));
        when(rolePermissions.isGranted(ProjectRole.VIEWER, Permission.TICKET_CREATE)).thenReturn(false);

        // When / Then
        assertThat(checker.hasPermission(2L, 7L, Permission.TICKET_CREATE)).isFalse();
    }

    @Test
    void hasPermission_returnsFalse_forNonMember() {
        // Given
        when(users.findById(2L)).thenReturn(Optional.of(user(2, PlatformRole.USER)));
        when(memberships.findByProjectIdAndUserId(7L, 2L)).thenReturn(Optional.empty());

        // When / Then
        assertThat(checker.hasPermission(2L, 7L, Permission.TICKET_CREATE)).isFalse();
    }

    @Test
    void require_returnsMembership_whenMemberRoleGrantsPermission() {
        // Given
        when(users.findById(2L)).thenReturn(Optional.of(user(2, PlatformRole.USER)));
        when(memberships.findByProjectIdAndUserId(7L, 2L))
                .thenReturn(Optional.of(membership(7L, 2L, ProjectRole.OWNER)));
        when(rolePermissions.isGranted(ProjectRole.OWNER, Permission.BOARD_CREATE)).thenReturn(true);

        // When
        ProjectMembership result = checker.require(2L, 7L, Permission.BOARD_CREATE);

        // Then
        assertThat(result.role()).isEqualTo(ProjectRole.OWNER);
    }

    @Test
    void require_returnsRealMembership_forAdminWithMembership() {
        // Given
        when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.ADMIN)));
        when(memberships.findByProjectIdAndUserId(7L, 1L))
                .thenReturn(Optional.of(membership(7L, 1L, ProjectRole.MEMBER)));

        // When
        ProjectMembership result = checker.require(1L, 7L, Permission.BOARD_CREATE);

        // Then
        assertThat(result.role()).isEqualTo(ProjectRole.MEMBER);
    }

    @Test
    void require_syntheticAdminMembership_usesInjectedClock() {
        // Given
        when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.ADMIN)));
        when(memberships.findByProjectIdAndUserId(7L, 1L)).thenReturn(Optional.empty());

        // When
        ProjectMembership result = checker.require(1L, 7L, Permission.BOARD_CREATE);

        // Then
        assertThat(result.createdAt()).isEqualTo(FIXED);
    }

    @Test
    void require_throwsProjectNotFound_forNonMemberNonAdmin() {
        // Given
        when(users.findById(2L)).thenReturn(Optional.of(user(2, PlatformRole.USER)));
        when(memberships.findByProjectIdAndUserId(7L, 2L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> checker.require(2L, 7L, Permission.BOARD_CREATE))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void require_throwsProjectAccessDenied_whenRoleLacksPermission() {
        // Given
        when(users.findById(2L)).thenReturn(Optional.of(user(2, PlatformRole.USER)));
        when(memberships.findByProjectIdAndUserId(7L, 2L))
                .thenReturn(Optional.of(membership(7L, 2L, ProjectRole.VIEWER)));
        when(rolePermissions.isGranted(ProjectRole.VIEWER, Permission.BOARD_CREATE)).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> checker.require(2L, 7L, Permission.BOARD_CREATE))
                .isInstanceOf(ProjectAccessDeniedException.class);
    }

    @Test
    void requireMembership_returnsMembership_forMember() {
        // Given
        when(users.findById(2L)).thenReturn(Optional.of(user(2, PlatformRole.USER)));
        when(memberships.findByProjectIdAndUserId(7L, 2L))
                .thenReturn(Optional.of(membership(7L, 2L, ProjectRole.VIEWER)));

        // When
        ProjectMembership result = checker.requireMembership(2L, 7L);

        // Then
        assertThat(result.role()).isEqualTo(ProjectRole.VIEWER);
    }

    @Test
    void requireMembership_returnsSyntheticOwner_forAdminWithoutMembership() {
        // Given
        when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.ADMIN)));
        when(memberships.findByProjectIdAndUserId(7L, 1L)).thenReturn(Optional.empty());

        // When
        ProjectMembership result = checker.requireMembership(1L, 7L);

        // Then
        assertThat(result.role()).isEqualTo(ProjectRole.OWNER);
    }

    @Test
    void requireMembership_throwsProjectNotFound_forNonMember() {
        // Given
        when(users.findById(2L)).thenReturn(Optional.of(user(2, PlatformRole.USER)));
        when(memberships.findByProjectIdAndUserId(7L, 2L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> checker.requireMembership(2L, 7L))
                .isInstanceOf(ProjectNotFoundException.class);
    }
}
