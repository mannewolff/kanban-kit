package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.project.domain.Project;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Der Name eines Projekts ohne Aufrufer (Issue #1998): Der Meldejob benennt damit das Projekt eines
 * Laufs in der Nachricht aufs Handy.
 *
 * <p>Eigene Klasse statt weiterer Tests in {@link ProjectServiceTest}: Jene Klasse steht an der
 * PMD-Schwelle {@code TooManyMethods}.
 */
class ProjectServiceNameTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private ProjectRepository projects;
  private PermissionChecker permissions;
  private ProjectService service;

  @BeforeEach
  void setUp() {
    projects = mock(ProjectRepository.class);
    permissions = mock(PermissionChecker.class);
    service =
        new ProjectService(
            projects,
            mock(ProjectMembershipRepository.class),
            permissions,
            mock(UserLookup.class),
            mock(InvitationMailer.class),
            mock(ApplicationEventPublisher.class),
            new AuthProperties("https://app.example", null, null, null, null),
            Clock.fixed(FIXED, ZoneOffset.UTC));
  }

  @Test
  void findName_nenntDenNamenOhneRechtepruefung() {
    when(projects.findById(9L))
        .thenReturn(Optional.of(new Project(9L, "Alpha", 2L, FIXED, null, false, null)));

    assertThat(service.findName(9L)).contains("Alpha");
    verifyNoInteractions(permissions);
  }

  @Test
  void findName_leerBeiUnbekanntemProjekt() {
    when(projects.findById(9L)).thenReturn(Optional.empty());

    assertThat(service.findName(9L)).isEmpty();
  }
}
