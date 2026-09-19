package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.project.application.DashboardParticipationReader.ParticipatingProject;
import org.mwolff.manban.project.domain.Project;

/**
 * Verhaltenstest des rechteprüfungsfreien Ports für die Teilnahme am Plattform-Leitstand (Issue
 * #1076). Liegt im selben Paket wie die package-private Implementierung — genau diese Sichtbarkeit
 * ist der Schutz: von außen ist nur der Port {@link DashboardParticipationReader} injizierbar.
 */
class ProjectDashboardParticipationServiceTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-16T10:00:00Z");

  @Test
  void participatingProjects_enthaeltNurTeilnehmendeProjekte() {
    // Given
    ProjectRepository projects = mock(ProjectRepository.class);
    when(projects.findAll())
        .thenReturn(
            List.of(projekt(9L, "Teilnimmt", true), projekt(10L, "Nimmt nicht teil", false)));
    DashboardParticipationReader service = new ProjectDashboardParticipationService(projects);

    // When / Then
    assertThat(service.participatingProjects())
        .containsExactly(new ParticipatingProject(9L, "Teilnimmt"));
  }

  @Test
  void participatingProjects_istLeerOhneTeilnehmendeProjekte() {
    ProjectRepository projects = mock(ProjectRepository.class);
    when(projects.findAll()).thenReturn(List.of(projekt(9L, "P", false)));
    DashboardParticipationReader service = new ProjectDashboardParticipationService(projects);

    assertThat(service.participatingProjects()).isEmpty();
  }

  @Test
  void isParticipating_liefertDieTeilnahmeDesProjekts() {
    ProjectRepository projects = mock(ProjectRepository.class);
    when(projects.findById(9L)).thenReturn(Optional.of(projekt(9L, "P", true)));
    DashboardParticipationReader service = new ProjectDashboardParticipationService(projects);

    assertThat(service.isParticipating(9L)).isTrue();
  }

  @Test
  void isParticipating_istFalseFuerEinUnbekanntesProjekt() {
    ProjectRepository projects = mock(ProjectRepository.class);
    when(projects.findById(9L)).thenReturn(Optional.empty());
    DashboardParticipationReader service = new ProjectDashboardParticipationService(projects);

    assertThat(service.isParticipating(9L)).isFalse();
  }

  private static Project projekt(long id, String name, boolean teilnahme) {
    return new Project(id, name, 1L, ANGELEGT, null, teilnahme);
  }
}
