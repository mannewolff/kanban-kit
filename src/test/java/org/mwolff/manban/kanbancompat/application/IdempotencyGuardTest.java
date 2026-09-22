package org.mwolff.manban.kanbancompat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.kanbancompat.application.IdempotencyRecordStore.IdempotencyKey;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Created;

/** Der Idempotenz-Guard: Wirkung einmal, Antwort beliebig oft (Issue #1001, Plan #995 E7). */
class IdempotencyGuardTest {

  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
  private static final String CREATE = "POST /items";

  private final InMemoryIdempotencyRecordStore store = new InMemoryIdempotencyRecordStore();
  private final IdempotencyGuard guard =
      new IdempotencyGuard(store, Clock.fixed(NOW, ZoneOffset.UTC));
  private final AtomicInteger effects = new AtomicInteger();

  private Created createOnce() {
    return new Created(100L + effects.incrementAndGet(), 7, true);
  }

  @Test
  void firstCall_runsTheEffect_andStoresTheAnswerWithStatusCreated() {
    Created first = guard.execute(1L, "k-1", CREATE, Created.class, this::createOnce);

    assertThat(first).isEqualTo(new Created(101L, 7, true));
    assertThat(effects).hasValue(1);
    assertThat(store.status(new IdempotencyKey(1L, "k-1"))).isEqualTo(201);
  }

  @Test
  void repeatedCall_returnsTheStoredAnswer_withoutRunningTheEffectAgain() {
    Created first = guard.execute(1L, "k-1", CREATE, Created.class, this::createOnce);

    Created again = guard.execute(1L, "k-1", CREATE, Created.class, this::createOnce);

    assertThat(again).isEqualTo(first);
    assertThat(effects).hasValue(1);
  }

  @Test
  void sameKeyInAnotherProject_isIndependent() {
    guard.execute(1L, "k-1", CREATE, Created.class, this::createOnce);

    Created other = guard.execute(2L, "k-1", CREATE, Created.class, this::createOnce);

    assertThat(other.id()).isEqualTo(102L);
    assertThat(effects).hasValue(2);
  }

  @Test
  void sameKeyForAnotherCommand_isRejected_andRunsNothing() {
    guard.execute(1L, "k-1", CREATE, Created.class, this::createOnce);

    assertThatThrownBy(
            () -> guard.execute(1L, "k-1", "POST /items/5/comments", effects::incrementAndGet))
        .isInstanceOf(IdempotencyKeyReusedException.class)
        .hasMessageContaining("k-1");
    assertThat(effects).hasValue(1);
  }

  @Test
  void commandWithoutAnswerBody_runsOnce_andRepeatsQuietly() {
    guard.execute(1L, "k-2", "POST /items/5/comments", effects::incrementAndGet);
    guard.execute(1L, "k-2", "POST /items/5/comments", effects::incrementAndGet);

    assertThat(effects).hasValue(1);
    assertThat(store.status(new IdempotencyKey(1L, "k-2"))).isEqualTo(201);
  }

  @Test
  void commandWithoutAnswerBody_rejectsTheKeyOfAnotherCommand() {
    guard.execute(1L, "k-2", "POST /items/5/comments", effects::incrementAndGet);

    assertThatThrownBy(
            () -> guard.execute(1L, "k-2", "POST /items/6/comments", effects::incrementAndGet))
        .isInstanceOf(IdempotencyKeyReusedException.class);
    assertThat(effects).hasValue(1);
  }

  @Test
  void answeredCommand_rejectsTheKeyOfCommandWithoutBody() {
    guard.execute(1L, "k-3", "POST /items/5/comments", effects::incrementAndGet);

    assertThatThrownBy(() -> guard.execute(1L, "k-3", CREATE, Created.class, this::createOnce))
        .isInstanceOf(IdempotencyKeyReusedException.class);
  }
}
