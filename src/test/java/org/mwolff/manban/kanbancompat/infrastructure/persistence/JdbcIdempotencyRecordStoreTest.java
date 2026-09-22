package org.mwolff.manban.kanbancompat.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.kanbancompat.application.IdempotencyRecordStore.IdempotencyKey;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die beiden Fehlerwege der JSON-Ablage (Issue #1001). Die Datenbankwege prüft {@code
 * KanbanCompatIdempotencyIT} gegen echtes PostgreSQL; hier geht es nur darum, dass ein nicht
 * ablegbarer oder nicht lesbarer Rumpf laut scheitert, statt eine leere Antwort auszuliefern.
 */
class JdbcIdempotencyRecordStoreTest {

  private static final IdempotencyKey KEY = new IdempotencyKey(1L, "k-1");

  private final ObjectMapper json = mock(ObjectMapper.class);
  private final JdbcIdempotencyRecordStore store =
      new JdbcIdempotencyRecordStore(mock(JdbcTemplate.class), json);

  @Test
  void unreadableStoredBody_failsLoudly_andNamesTheKey() throws Exception {
    when(json.readValue("{", Object.class)).thenThrow(new JsonProcessingException("kaputt") {});

    assertThatThrownBy(() -> store.decode(KEY, "{", Object.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("k-1");
  }

  @Test
  void unwritableBody_failsLoudly() throws Exception {
    when(json.writeValueAsString(any())).thenThrow(new JsonProcessingException("kaputt") {});

    assertThatThrownBy(() -> store.complete(KEY, 201, new Object()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JSON");
  }
}
