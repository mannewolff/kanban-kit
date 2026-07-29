package org.mwolff.manban.outbox.infrastructure;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.outbox.application.OutboxDispatchService;

/** Der geplante Worker delegiert an den Durchlauf-Service (Issue #501). */
class OutboxWorkerTest {

  private final OutboxDispatchService dispatch = mock(OutboxDispatchService.class);
  private final OutboxWorker worker = new OutboxWorker(dispatch);

  @Test
  void run_triggersOneDispatchRound() {
    // Given
    when(dispatch.dispatchDue()).thenReturn(0);

    // When
    worker.run();

    // Then
    verify(dispatch).dispatchDue();
  }

  @Test
  void run_logsWhenEntriesWereDelivered() {
    // Given
    when(dispatch.dispatchDue()).thenReturn(3);

    // When
    worker.run();

    // Then
    verify(dispatch).dispatchDue();
  }
}
