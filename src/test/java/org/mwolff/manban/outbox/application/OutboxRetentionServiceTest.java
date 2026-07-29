package org.mwolff.manban.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Aufbewahrung erledigter Outbox-Einträge (Issue #501). */
class OutboxRetentionServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-28T10:00:00Z");

  private final OutboxRepository repository = mock(OutboxRepository.class);
  private final OutboxRetentionService service = new OutboxRetentionService(repository);

  @Test
  void purgeCompleted_deletesEverythingFinishedBeforeTheThreshold() {
    // Given
    when(repository.deleteCompletedBefore(NOW.minus(Duration.ofDays(7)))).thenReturn(3);

    // When
    int purged = service.purgeCompleted(NOW, 7);

    // Then
    assertThat(purged).isEqualTo(3);
    verify(repository).deleteCompletedBefore(NOW.minus(Duration.ofDays(7)));
  }

  @Test
  void purgeCompleted_withoutRetentionPeriodKeepsEverything() {
    // Given / When
    int purged = service.purgeCompleted(NOW, 0);

    // Then
    assertThat(purged).isZero();
    verify(repository, org.mockito.Mockito.never())
        .deleteCompletedBefore(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void purgeCompleted_withNegativeRetentionPeriodAlsoKeepsEverything() {
    // Given / When
    int purged = service.purgeCompleted(NOW, -1);

    // Then
    assertThat(purged).isZero();
  }
}
