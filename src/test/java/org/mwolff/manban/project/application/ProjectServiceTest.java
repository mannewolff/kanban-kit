package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.project.domain.Project;

/** Zeit-Test: der Anlege-Zeitstempel eines Projekts stammt aus der injizierten Clock. */
class ProjectServiceTest {

    private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void create_setsCreatedAtFromInjectedClock() {
        // Given
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectMembershipRepository memberships = mock(ProjectMembershipRepository.class);
        PermissionChecker permissions = mock(PermissionChecker.class);
        AppUserRepository users = mock(AppUserRepository.class);
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
        when(permissions.isPlatformAdmin(1L)).thenReturn(true);
        when(users.findByEmail("owner@x.de")).thenReturn(Optional.of(
                new AppUser(2L, "owner@x.de", "hash", "Owner", true, PlatformRole.USER)));
        when(projects.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
        ProjectService service = new ProjectService(projects, memberships, permissions, users, clock);

        // When
        service.create(1L, "Neu", "owner@x.de");

        // Then
        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projects).save(captor.capture());
        assertThat(captor.getValue().createdAt()).isEqualTo(FIXED);
    }
}
