package org.mwolff.manban.kanbancompat.application;

import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;
import org.mwolff.manban.kanbancompat.application.IdempotencyRecordStore.IdempotencyKey;
import org.mwolff.manban.kanbancompat.application.IdempotencyRecordStore.Stored;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lässt eine anlegende Wirkung je Schlüssel genau einmal eintreten und beantwortet jede
 * Wiederholung mit der abgelegten Antwort (Issue #1001, Plan #995 E7).
 *
 * <p>Der Schlüssel wird in <strong>derselben</strong> Transaktion beansprucht, in der die Wirkung
 * eintritt ({@link Propagation#MANDATORY}): kein zweiter Schreibweg, kein Zwei-Phasen-Protokoll.
 * Eine gleichzeitige Wiederholung wartet am Unique-Index, bis die erste Ausführung committet — und
 * bekommt dann deren Antwort — oder zurückrollt — und führt dann selbst aus.
 *
 * <p>Beide geschützten Befehle antworten mit {@code 201}; der Status wird trotzdem abgelegt, damit
 * die Ablage für sich lesbar bleibt.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class IdempotencyGuard {

  /** Status beider geschützter Befehle ({@code POST /items}, {@code POST /items/{id}/comments}). */
  static final int CREATED = 201;

  private final IdempotencyRecordStore store;
  private final Clock clock;

  public IdempotencyGuard(IdempotencyRecordStore store, Clock clock) {
    this.store = store;
    this.clock = clock;
  }

  /** Führt einen Befehl mit Antwortrumpf höchstens einmal je Projekt und Schlüssel aus. */
  public <T> T execute(
      long projectId, String key, String endpoint, Class<T> type, Supplier<T> action) {
    IdempotencyKey id = new IdempotencyKey(projectId, key);
    if (store.claim(id, endpoint, clock.instant())) {
      T result = action.get();
      store.complete(id, CREATED, result);
      return result;
    }
    Stored stored = storedFor(id, endpoint);
    return store.decode(id, Objects.requireNonNull(stored.body(), "abgelegter Rumpf"), type);
  }

  /** Führt einen Befehl ohne Antwortrumpf höchstens einmal je Projekt und Schlüssel aus. */
  public void execute(long projectId, String key, String endpoint, Runnable action) {
    IdempotencyKey id = new IdempotencyKey(projectId, key);
    if (store.claim(id, endpoint, clock.instant())) {
      action.run();
      store.complete(id, CREATED, null);
      return;
    }
    storedFor(id, endpoint);
  }

  /** Die abgelegte Ausführung — sofern sie zu demselben Befehl gehört. */
  private Stored storedFor(IdempotencyKey id, String endpoint) {
    Stored stored = store.load(id);
    if (!stored.endpoint().equals(endpoint)) {
      throw new IdempotencyKeyReusedException(id.value(), stored.endpoint());
    }
    return stored;
  }
}
