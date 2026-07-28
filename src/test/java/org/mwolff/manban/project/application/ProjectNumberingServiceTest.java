package org.mwolff.manban.project.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

/**
 * Verhaltenstest des rechteprüfungsfreien Schreib-Ports (Issue #463). Liegt im selben Paket wie die
 * package-private Implementierung — genau diese Sichtbarkeit ist der Schutz: von außen ist nur der
 * Port {@link NextCardNumberWriter} injizierbar.
 */
class ProjectNumberingServiceTest {

  @Test
  void setNextCardNumber_delegatesToRepository() {
    // Given
    ProjectRepository projects = mock(ProjectRepository.class);
    NextCardNumberWriter service = new ProjectNumberingService(projects);

    // When
    service.setNextCardNumber(9L, 13457);

    // Then
    verify(projects).setNextCardNumber(9L, 13457);
  }
}
