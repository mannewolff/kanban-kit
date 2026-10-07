package org.mwolff.manban.card.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mwolff.manban.card.application.DoneRetentionService;
import org.mwolff.manban.card.application.DoneRetentionSettingService;
import org.mwolff.manban.card.application.VorhabenArchivierung;

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
    DoneRetentionJob job =
        new DoneRetentionJob(retention, retentionSetting, mock(VorhabenArchivierung.class), clock);

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
    DoneRetentionJob job =
        new DoneRetentionJob(retention, retentionSetting, mock(VorhabenArchivierung.class), clock);

    // When
    job.run();

    // Then
    verify(retention).archiveExpiredDoneCards(FIXED, 7);
  }

  @Test
  void run_gleichtVorhabenNachDerKartenArchivierungAb() {
    // Given
    DoneRetentionService retention = mock(DoneRetentionService.class);
    DoneRetentionSettingService retentionSetting = mock(DoneRetentionSettingService.class);
    VorhabenArchivierung vorhaben = mock(VorhabenArchivierung.class);
    when(retentionSetting.effectiveRetentionDays()).thenReturn(30);
    when(vorhaben.gleicheAlleAb()).thenReturn(2);
    DoneRetentionJob job =
        new DoneRetentionJob(
            retention, retentionSetting, vorhaben, Clock.fixed(FIXED, ZoneOffset.UTC));

    // When
    job.run();

    // Then: eben archivierte Karten zaehlen beim Vorhaben-Abgleich schon nicht mehr
    InOrder order = inOrder(retention, vorhaben);
    order.verify(retention).archiveExpiredDoneCards(FIXED, 30);
    order.verify(vorhaben).gleicheAlleAb();
  }

  @Test
  void run_gleichtVorhabenAuchBeiAufbewahrungNullAb() {
    // Given: Karten-Archivierung abgeschaltet (AK 3), der Abgleich findet nichts -> kein Log
    DoneRetentionService retention = mock(DoneRetentionService.class);
    DoneRetentionSettingService retentionSetting = mock(DoneRetentionSettingService.class);
    VorhabenArchivierung vorhaben = mock(VorhabenArchivierung.class);
    when(retentionSetting.effectiveRetentionDays()).thenReturn(0);
    when(vorhaben.gleicheAlleAb()).thenReturn(0);
    DoneRetentionJob job =
        new DoneRetentionJob(
            retention, retentionSetting, vorhaben, Clock.fixed(FIXED, ZoneOffset.UTC));

    // When
    job.run();

    // Then
    verify(vorhaben).gleicheAlleAb();
  }
}
