package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.project.domain.ProjectMembership;

/** Zeit-Test: die synthetische Admin-Mitgliedschaft trägt den Clock-Zeitpunkt. */
class PermissionCheckerTest {

    private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void require_syntheticAdminMembership_usesInjectedClock() {
        // Given
        ProjectMembershipRepository memberships = mock(ProjectMembershipRepository.class);
        RolePermissionRepository rolePermissions = mock(RolePermissionRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
        when(users.findById(1L)).thenReturn(Optional.of(
                new AppUser(1L, "admin@x.de", "hash", "Admin", true, PlatformRole.ADMIN)));
        when(memberships.findByProjectIdAndUserId(7L, 1L)).thenReturn(Optional.empty());
        PermissionChecker checker = new PermissionChecker(memberships, rolePermissions, users, clock);

        // When
        ProjectMembership result = checker.require(1L, 7L, Permission.BOARD_CREATE);

        // Then
        assertThat(result.createdAt()).isEqualTo(FIXED);
    }
}
