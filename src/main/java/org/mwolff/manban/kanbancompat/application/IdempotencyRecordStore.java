package org.mwolff.manban.kanbancompat.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Port auf die abgelegten Idempotenz-Schlüssel (Issue #1001, Plan #995 E7).
 *
 * <p>Alle Methoden außer {@link #deleteOlderThan} laufen in der Transaktion des Aufrufers — genau
 * das macht aus Ablage und fachlicher Wirkung eine Einheit: Rollt die Wirkung zurück, verschwindet
 * auch der Schlüssel, und eine Wiederholung darf es erneut versuchen.
 */
public interface IdempotencyRecordStore {

  /**
   * Schlüssel einer Ablage: je Projekt, damit derselbe Wert in zwei Projekten zweimal gilt.
   *
   * @param projectId das Projekt des gebundenen Boards
   * @param value der vom Aufrufer gesetzte Schlüssel
   */
  record IdempotencyKey(long projectId, String value) {}

  /**
   * Die abgelegte Ausführung.
   *
   * @param endpoint der Befehl, für den der Schlüssel zuerst verwendet wurde
   * @param status der Statuscode der ersten Antwort
   * @param body der Antwortrumpf in abgelegter Form; {@code null} bei einem Befehl ohne Rumpf
   */
  record Stored(String endpoint, int status, @Nullable String body) {}

  /**
   * Beansprucht den Schlüssel für diesen Befehl.
   *
   * <p>Ist der Schlüssel in einer noch offenen Transaktion beansprucht, <strong>wartet</strong> der
   * Aufruf, bis sie endet: Committet sie, liefert er {@code false}; rollt sie zurück, gehört der
   * Schlüssel diesem Aufruf.
   *
   * @return {@code true}, wenn der Schlüssel neu ist und die Wirkung jetzt eintreten soll
   */
  boolean claim(IdempotencyKey key, String endpoint, Instant now);

  /** Die abgelegte Ausführung; setzt voraus, dass {@link #claim} {@code false} geliefert hat. */
  Stored load(IdempotencyKey key);

  /** Legt Status und Antwortrumpf der gerade eingetretenen Wirkung ab. */
  void complete(IdempotencyKey key, int status, @Nullable Object body);

  /** Liest einen abgelegten Antwortrumpf in seine ursprüngliche Form zurück. */
  <T> T decode(IdempotencyKey key, String body, Class<T> type);

  /** Löscht alle Ablagen, die vor {@code cutoff} entstanden sind, und liefert ihre Zahl. */
  int deleteOlderThan(Instant cutoff);
}
