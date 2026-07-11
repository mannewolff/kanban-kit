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
import org.mwolff.manban.card.application.DoneRetentionService;

/** Zeit-Test: der Aufräum-Job reicht den Clock-Zeitpunkt an die Retention weiter. */
class DoneRetentionJobTest {

    private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void run_passesClockInstantToRetention() {
        // Given
        DoneRetentionService retention = mock(DoneRetentionService.class);
        CleanupProperties properties = new CleanupProperties(true, 30, null);
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
        when(retention.archiveExpiredDoneCards(FIXED, 30)).thenReturn(0);
        DoneRetentionJob job = new DoneRetentionJob(retention, properties, clock);

        // When
        job.run();

        // Then
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(retention).archiveExpiredDoneCards(captor.capture(), anyInt());
        assertThat(captor.getValue()).isEqualTo(FIXED);
    }
}
