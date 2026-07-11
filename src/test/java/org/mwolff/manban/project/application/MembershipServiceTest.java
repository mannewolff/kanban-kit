package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.project.domain.Project;
import org.mwolff.manban.project.domain.ProjectInvitation;
import org.mwolff.manban.project.domain.ProjectRole;

/** Zeit-Test: das Ablaufdatum einer Einladung ist Clock-Zeitpunkt plus konfigurierte TTL. */
class MembershipServiceTest {

    private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void invite_setsExpiryFromInjectedClockPlusTtl() {
        // Given
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectMembershipRepository memberships = mock(ProjectMembershipRepository.class);
        ProjectInvitationRepository invitations = mock(ProjectInvitationRepository.class);
        PermissionChecker permissions = mock(PermissionChecker.class);
        InvitationMailer mailer = mock(InvitationMailer.class);
        AppUserRepository users = mock(AppUserRepository.class);
        AuthProperties authProperties = new AuthProperties(null, null, null, null, null, null);
        ProjectProperties projectProperties = new ProjectProperties(Duration.ofDays(7));
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
        when(projects.findById(9L)).thenReturn(Optional.of(new Project(9L, "P", 1L, FIXED)));
        when(invitations.save(any(ProjectInvitation.class))).thenAnswer(inv -> inv.getArgument(0));
        MembershipService service = new MembershipService(projects, memberships, invitations, permissions,
                mailer, users, authProperties, projectProperties, clock);

        // When
        service.invite(1L, 9L, "guest@x.de", ProjectRole.MEMBER);

        // Then
        ArgumentCaptor<ProjectInvitation> captor = ArgumentCaptor.forClass(ProjectInvitation.class);
        verify(invitations).save(captor.capture());
        assertThat(captor.getValue().expiresAt()).isEqualTo(FIXED.plus(Duration.ofDays(7)));
    }
}
