package org.mwolff.manban.project.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Verhaltenstest des rechteprüfungsfreien Schreib-Ports für den Erfassungsbeginn (Issue #1012).
 * Liegt im selben Paket wie die package-private Implementierung — genau diese Sichtbarkeit ist der
 * Schutz: von außen ist nur der Port {@link InteractiveUsageSinceWriter} injizierbar.
 *
 * <p>Ob der Wert schon steht, entscheidet die Persistenz und nicht dieser Dienst; hier steht nur,
 * dass er unverändert durchreicht.
 */
class ProjectInteractiveUsageServiceTest {

  @Test
  void setInteractiveUsageSinceIfAbsent_delegatesToRepository() {
    // Given
    Instant startedAt = Instant.parse("2026-09-16T10:00:00Z");
    ProjectRepository projects = mock(ProjectRepository.class);
    InteractiveUsageSinceWriter service = new ProjectInteractiveUsageService(projects);

    // When
    service.setInteractiveUsageSinceIfAbsent(9L, startedAt);

    // Then
    verify(projects).setInteractiveUsageSinceIfAbsent(9L, startedAt);
  }
}
