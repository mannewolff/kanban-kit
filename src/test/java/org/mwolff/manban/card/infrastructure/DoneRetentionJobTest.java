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
import org.mwolff.manban.card.application.DoneRetentionService;
import org.mwolff.manban.card.application.DoneRetentionSettingService;

/** Zeit-Test: der Aufräum-Job reicht Clock-Zeitpunkt und effektiven Retention-Wert weiter. */
class DoneRetentionJobTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  @Test
  void run_passesClockInstantToRetention() {
    // Given
    DoneRetentionService retention = mock(DoneRetentionService.class);
    DoneRetentionSettingService retentionSetting = mock(DoneRetentionSettingService.class);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    when(retentionSetting.effectiveRetentionDays()).thenReturn(30);
    when(retention.archiveExpiredDoneCards(FIXED, 30)).thenReturn(0);
    DoneRetentionJob job = new DoneRetentionJob(retention, retentionSetting, clock);

    // When
    job.run();

    // Then
    ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
    verify(retention).archiveExpiredDoneCards(captor.capture(), anyInt());
    assertThat(captor.getValue()).isEqualTo(FIXED);
  }

  @Test
  void run_passesEffectiveRetentionDays_andLogsWhenCardsArchived() {
    // Given: der effektive Wert kommt aus dem Setting-Service; die Retention meldet > 0 archivierte
    // Karten -> Log-Zweig wird betreten
    DoneRetentionService retention = mock(DoneRetentionService.class);
    DoneRetentionSettingService retentionSetting = mock(DoneRetentionSettingService.class);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    when(retentionSetting.effectiveRetentionDays()).thenReturn(7);
    when(retention.archiveExpiredDoneCards(FIXED, 7)).thenReturn(5);
    DoneRetentionJob job = new DoneRetentionJob(retention, retentionSetting, clock);

    // When
    job.run();

    // Then
    verify(retention).archiveExpiredDoneCards(FIXED, 7);
  }
}
