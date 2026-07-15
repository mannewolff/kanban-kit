package org.mwolff.manban.card.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.card.application.CleanupProperties;
import org.mwolff.manban.card.application.TrashRetentionService;

/**
 * Zeit-Test: der Papierkorb-Job reicht den Clock-Zeitpunkt und die Frist an die Retention weiter.
 */
class TrashRetentionJobTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  @Test
  void run_passesClockInstantAndDaysToRetention() {
    TrashRetentionService retention = mock(TrashRetentionService.class);
    CleanupProperties properties = new CleanupProperties(true, 30, 21, null);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    when(retention.purgeExpiredTrash(FIXED, 21)).thenReturn(0);
    TrashRetentionJob job = new TrashRetentionJob(retention, properties, clock);

    job.run();

    ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
    verify(retention).purgeExpiredTrash(captor.capture(), anyInt());
    assertThat(captor.getValue()).isEqualTo(FIXED);
  }

  @Test
  void run_logsBranch_whenCardsPurged() {
    TrashRetentionService retention = mock(TrashRetentionService.class);
    CleanupProperties properties = new CleanupProperties(true, 30, 21, null);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    when(retention.purgeExpiredTrash(FIXED, 21)).thenReturn(3);
    TrashRetentionJob job = new TrashRetentionJob(retention, properties, clock);

    job.run();

    verify(retention).purgeExpiredTrash(FIXED, 21);
  }
}
