package org.mwolff.manban.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Ein Worker-Durchlauf über die fälligen Einträge (Issue #501). */
class OutboxDispatchServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-28T10:00:00Z");

  private final OutboxRepository repository = mock(OutboxRepository.class);
  private final OutboxEntryDispatcher entryDispatcher = mock(OutboxEntryDispatcher.class);
  private final OutboxDispatchService service =
      new OutboxDispatchService(
          repository,
          entryDispatcher,
          new OutboxProperties(true, 5000L, 3, 8, Duration.ofSeconds(10), Duration.ofHours(1), 7),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void dispatchDue_asksForAtMostTheConfiguredBatchSize() {
    // Given
    when(repository.findDueIds(NOW, 3)).thenReturn(List.of());

    // When
    service.dispatchDue();

    // Then
    verify(repository).findDueIds(NOW, 3);
  }

  @Test
  void dispatchDue_dispatchesEveryDueEntry() {
    // Given
    when(repository.findDueIds(NOW, 3)).thenReturn(List.of(7L, 9L));
    when(entryDispatcher.dispatch(7L)).thenReturn(true);
    when(entryDispatcher.dispatch(9L)).thenReturn(true);

    // When
    service.dispatchDue();

    // Then
    verify(entryDispatcher).dispatch(7L);
    verify(entryDispatcher).dispatch(9L);
  }

  @Test
  void dispatchDue_countsOnlyTheSuccessfullyDeliveredEntries() {
    // Given — bewusst 2:1 statt 1:1: Bei ausgeglichenem Mix zählte auch die negierte Bedingung
    // dasselbe Ergebnis, der Zähler-Mutant wäre nicht tötbar.
    when(repository.findDueIds(NOW, 3)).thenReturn(List.of(7L, 9L, 11L));
    when(entryDispatcher.dispatch(7L)).thenReturn(true);
    when(entryDispatcher.dispatch(9L)).thenReturn(false);
    when(entryDispatcher.dispatch(11L)).thenReturn(true);

    // When
    int dispatched = service.dispatchDue();

    // Then
    assertThat(dispatched).isEqualTo(2);
  }

  @Test
  void dispatchDue_doesNothingWhenNoEntryIsDue() {
    // Given
    when(repository.findDueIds(NOW, 3)).thenReturn(List.of());

    // When
    int dispatched = service.dispatchDue();

    // Then
    assertThat(dispatched).isZero();
    verifyNoInteractions(entryDispatcher);
  }
}
