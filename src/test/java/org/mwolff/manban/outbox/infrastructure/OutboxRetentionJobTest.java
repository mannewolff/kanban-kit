package org.mwolff.manban.outbox.infrastructure;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.outbox.application.OutboxProperties;
import org.mwolff.manban.outbox.application.OutboxRetentionService;

/** Der geplante Aufräum-Job reicht Zeitpunkt und Aufbewahrungsdauer weiter (Issue #501). */
class OutboxRetentionJobTest {

  private static final Instant FIXED = Instant.parse("2026-07-28T10:00:00Z");

  private final OutboxRetentionService retention = mock(OutboxRetentionService.class);

  private OutboxRetentionJob job(int retentionDays) {
    return new OutboxRetentionJob(
        retention,
        new OutboxProperties(
            true, 5000L, 50, 8, Duration.ofSeconds(10), Duration.ofHours(1), retentionDays),
        Clock.fixed(FIXED, ZoneOffset.UTC));
  }

  @Test
  void run_passesClockInstantAndConfiguredRetention() {
    // Given
    when(retention.purgeCompleted(FIXED, 7)).thenReturn(0);

    // When
    job(7).run();

    // Then
    verify(retention).purgeCompleted(FIXED, 7);
  }

  @Test
  void run_logsWhenEntriesWerePurged() {
    // Given
    when(retention.purgeCompleted(FIXED, 30)).thenReturn(5);

    // When
    job(30).run();

    // Then
    verify(retention).purgeCompleted(FIXED, 30);
  }
}
