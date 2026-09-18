package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.project.domain.Project;

/**
 * Verhaltenstest der rechteprüfungsfreien Ports für den Erfassungsbeginn (Issue #1012, Lesen seit
 * Issue #1013). Liegt im selben Paket wie die package-private Implementierung — genau diese
 * Sichtbarkeit ist der Schutz: von außen sind nur die Ports {@link InteractiveUsageSinceWriter} und
 * {@link InteractiveUsageSinceReader} injizierbar.
 *
 * <p>Ob der Wert schon steht, entscheidet die Persistenz und nicht dieser Dienst; hier steht nur,
 * dass er unverändert durchreicht.
 */
class ProjectInteractiveUsageServiceTest {

  private static final Instant ERFASST_SEIT = Instant.parse("2026-09-16T10:00:00Z");

  @Test
  void setInteractiveUsageSinceIfAbsent_delegatesToRepository() {
    // Given
    ProjectRepository projects = mock(ProjectRepository.class);
    InteractiveUsageSinceWriter service = new ProjectInteractiveUsageService(projects);

    // When
    service.setInteractiveUsageSinceIfAbsent(9L, ERFASST_SEIT);

    // Then
    verify(projects).setInteractiveUsageSinceIfAbsent(9L, ERFASST_SEIT);
  }

  @Test
  void interactiveUsageSince_liefertDenZeitpunktDesProjekts() {
    ProjectRepository projects = mock(ProjectRepository.class);
    when(projects.findById(9L)).thenReturn(Optional.of(projekt(ERFASST_SEIT)));
    InteractiveUsageSinceReader service = new ProjectInteractiveUsageService(projects);

    assertThat(service.interactiveUsageSince(9L)).contains(ERFASST_SEIT);
  }

  /** Solange keine Sitzung gemeldet wurde, gibt es den Zeitpunkt nicht. */
  @Test
  void interactiveUsageSince_istLeerOhneGemeldeteSitzung() {
    ProjectRepository projects = mock(ProjectRepository.class);
    when(projects.findById(9L)).thenReturn(Optional.of(projekt(null)));
    InteractiveUsageSinceReader service = new ProjectInteractiveUsageService(projects);

    assertThat(service.interactiveUsageSince(9L)).isEmpty();
  }

  @Test
  void interactiveUsageSince_istLeerFuerEinUnbekanntesProjekt() {
    ProjectRepository projects = mock(ProjectRepository.class);
    when(projects.findById(9L)).thenReturn(Optional.empty());
    InteractiveUsageSinceReader service = new ProjectInteractiveUsageService(projects);

    assertThat(service.interactiveUsageSince(9L)).isEmpty();
  }

  private static Project projekt(@Nullable Instant seit) {
    return new Project(9L, "P", 1L, Instant.parse("2026-01-01T00:00:00Z"), seit);
  }
}
