package org.mwolff.manban.ratelimit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.ratelimit.MutableClock;
import org.mwolff.manban.ratelimit.infrastructure.persistence.OverloadRejectionTable.HourKey;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Der Adapter schreibt gepuffert, nicht je Abweisung (Issue #1000, Plan #995 E14): Eine Zeile je
 * Abweisung machte gerade den Überlastfall zum Schreiblastfall.
 */
class BufferedRejectionRecorderTest {

  private static final Instant NOW = Instant.parse("2026-09-22T10:17:42Z");
  private static final Instant HOUR = Instant.parse("2026-09-22T10:00:00Z");

  private final MutableClock clock = new MutableClock(NOW);
  private final OverloadRejectionTable table = mock(OverloadRejectionTable.class);
  private final BufferedRejectionRecorder recorder = new BufferedRejectionRecorder(table, clock);

  private Map<HourKey, Integer> flushedBatch() {
    ArgumentCaptor<Map<HourKey, Integer>> batch = captor();
    verify(table).add(batch.capture());
    return batch.getValue();
  }

  @Test
  void manyRejectionsInOneBufferWindow_becomeOneWrite() {
    IntStream.range(0, 100).forEach(i -> recorder.record(7L));

    recorder.flush();

    verify(table, times(1)).add(any());
    assertThat(flushedBatch()).containsExactly(Map.entry(new HourKey(7L, HOUR), 100));
  }

  @Test
  void rejectionsAreBucketedByPersonAndFullHour() {
    recorder.record(7L);
    recorder.record(8L);
    clock.advance(Duration.ofHours(1));
    recorder.record(7L);

    recorder.flush();

    assertThat(flushedBatch())
        .containsOnly(
            Map.entry(new HourKey(7L, HOUR), 1),
            Map.entry(new HourKey(8L, HOUR), 1),
            Map.entry(new HourKey(7L, HOUR.plus(Duration.ofHours(1))), 1));
  }

  @Test
  void flush_emptiesTheBuffer() {
    recorder.record(7L);
    recorder.flush();

    recorder.flush();

    verify(table, times(1)).add(any());
  }

  @Test
  void flush_withNothingBuffered_writesNothing() {
    recorder.flush();

    verify(table, never()).add(any());
  }

  @Test
  void failedWrite_keepsTheCountsForTheNextFlush() {
    // Given: Die Datenbank ist beim ersten Versuch nicht erreichbar.
    recorder.record(7L);
    recorder.record(7L);
    doThrow(new DataAccessResourceFailureException("Datenbank weg"))
        .doNothing()
        .when(table)
        .add(any());
    assertThatThrownBy(recorder::flush).isInstanceOf(DataAccessResourceFailureException.class);

    // When: eine weitere Abweisung, dann der nächste Versuch.
    recorder.record(7L);
    recorder.flush();

    // Then: Nichts ist verloren — beim zweiten Schreiben stehen alle drei da.
    ArgumentCaptor<Map<HourKey, Integer>> batch = captor();
    verify(table, times(2)).add(batch.capture());
    assertThat(batch.getAllValues().getLast()).containsExactly(Map.entry(new HourKey(7L, HOUR), 3));
  }

  @Test
  void shutdown_flushesWhatIsStillBuffered() {
    recorder.record(7L);

    recorder.flushOnShutdown();

    assertThat(flushedBatch()).containsExactly(Map.entry(new HourKey(7L, HOUR), 1));
  }

  // Mockito kann generische Typen nicht ohne ungeprüfte Umwandlung einfangen.
  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<Map<HourKey, Integer>> captor() {
    return ArgumentCaptor.forClass(Map.class);
  }
}
