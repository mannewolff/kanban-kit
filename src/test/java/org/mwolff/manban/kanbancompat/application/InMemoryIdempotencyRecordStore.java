package org.mwolff.manban.kanbancompat.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Speicher für die Unit-Tests des Idempotenz-Guards (Issue #1001). Bildet die Semantik des echten
 * Adapters nach — {@code claim} gelingt genau einmal je Projekt und Schlüssel —, speichert den
 * Antwortrumpf aber als Objekt statt als JSON: Die Serialisierung ist Sache des Adapters und im IT
 * gegen echtes PostgreSQL geprüft.
 */
final class InMemoryIdempotencyRecordStore implements IdempotencyRecordStore {

  private record Row(String endpoint, Instant createdAt, int status, @Nullable Object body) {}

  private final Map<IdempotencyKey, Row> rows = new HashMap<>();

  @Override
  public boolean claim(IdempotencyKey key, String endpoint, Instant now) {
    return rows.putIfAbsent(key, new Row(endpoint, now, 0, null)) == null;
  }

  @Override
  public Stored load(IdempotencyKey key) {
    Row row = Objects.requireNonNull(rows.get(key));
    return new Stored(row.endpoint(), row.status(), row.body() == null ? null : "stored");
  }

  @Override
  public void complete(IdempotencyKey key, int status, @Nullable Object body) {
    Row row = Objects.requireNonNull(rows.get(key));
    rows.put(key, new Row(row.endpoint(), row.createdAt(), status, body));
  }

  @Override
  public <T> T decode(IdempotencyKey key, String body, Class<T> type) {
    return type.cast(Objects.requireNonNull(rows.get(key)).body());
  }

  @Override
  public int deleteOlderThan(Instant cutoff) {
    int before = rows.size();
    rows.values().removeIf(row -> row.createdAt().isBefore(cutoff));
    return before - rows.size();
  }

  int size() {
    return rows.size();
  }

  int status(IdempotencyKey key) {
    return Objects.requireNonNull(rows.get(key)).status();
  }
}
