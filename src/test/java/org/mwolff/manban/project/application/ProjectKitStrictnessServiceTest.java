package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.project.domain.Project;

/**
 * Verhaltenstest der rechteprüfungsfreien Ports für die Strenge-Stufe des Kits (Issue #2014). Liegt
 * im selben Paket wie die package-private Implementierung — von außen sind nur die Ports {@link
 * KitStrictnessWriter} und {@link KitStrictnessReader} injizierbar.
 */
class ProjectKitStrictnessServiceTest {

  private static final Instant ANGELEGT = Instant.parse("2026-10-01T10:00:00Z");

  @Test
  void reportStrictness_reichtAnDasRepositoryDurch() {
    ProjectRepository projects = mock(ProjectRepository.class);
    KitStrictnessWriter service = new ProjectKitStrictnessService(projects);

    service.reportStrictness(9L, "strict");

    verify(projects).setKitStrictness(9L, "strict");
  }

  @Test
  void reportedStrictness_nenntNurProjekteMitMeldung() {
    ProjectRepository projects = mock(ProjectRepository.class);
    when(projects.findAll())
        .thenReturn(List.of(projekt(1L, "light"), projekt(2L, null), projekt(3L, "strict")));
    KitStrictnessReader service = new ProjectKitStrictnessService(projects);

    assertThat(service.reportedStrictness()).isEqualTo(Map.of(1L, "light", 3L, "strict"));
  }

  private static Project projekt(long id, @Nullable String stufe) {
    return new Project(id, "P" + id, 1L, ANGELEGT, null, false, null, stufe);
  }
}
