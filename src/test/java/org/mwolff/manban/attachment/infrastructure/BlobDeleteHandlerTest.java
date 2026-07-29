package org.mwolff.manban.attachment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.attachment.application.ObjectStorage;
import org.mwolff.manban.outbox.application.OutboxHandler;

/** Ausführung des vorgemerkten Blob-Löschauftrags (Issue #503). */
class BlobDeleteHandlerTest {

  private final ObjectStorage storage = mock(ObjectStorage.class);
  private final OutboxHandler handler = new BlobDeleteHandler(storage);

  @Test
  void eventType_matchesTheSchedulerType() {
    assertThat(handler.eventType()).isEqualTo("attachment.blob-delete");
  }

  @Test
  void handle_deletesTheObjectNamedByThePayload() {
    // Given / When
    handler.handle("cards/5/abc-123");

    // Then
    verify(storage).delete("cards/5/abc-123");
  }
}
