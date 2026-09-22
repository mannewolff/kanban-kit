package org.mwolff.manban.kanbancompat.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.kanbancompat.application.IdempotencyRecordStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ablage der Idempotenz-Schlüssel in {@code idempotency_record} (Issue #1001, Plan #995 E7).
 *
 * <p><strong>Warum {@code ON CONFLICT DO NOTHING} statt eines abgefangenen
 * Unique-Verstoßes:</strong> In PostgreSQL bricht ein Unique-Verstoß die ganze Transaktion ab;
 * danach ließe sich in ihr nicht einmal mehr die abgelegte Antwort lesen. {@code ON CONFLICT DO
 * NOTHING} entscheidet dasselbe über denselben Index, liefert aber nur „0 Zeilen" — und wartet,
 * solange eine andere Transaktion denselben Schlüssel offen hält.
 *
 * <p><strong>Warum JDBC in einer JPA-Transaktion:</strong> {@link JdbcTemplate} läuft auf der
 * Verbindung der laufenden Transaktion mit. Schlüssel und fachliche Wirkung committen oder rollen
 * damit gemeinsam zurück.
 *
 * <p>Der Antwortrumpf wird mit dem {@link ObjectMapper} der Anwendung abgelegt — demselben, der die
 * erste Antwort ausliefert. Zurückgelesen entsteht daraus dasselbe Objekt und bei der Auslieferung
 * dieselbe Bytefolge.
 */
@Component
public class JdbcIdempotencyRecordStore implements IdempotencyRecordStore {

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public JdbcIdempotencyRecordStore(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public boolean claim(IdempotencyKey key, String endpoint, Instant now) {
    return jdbc.update(
            "INSERT INTO idempotency_record (project_id, key, endpoint, created_at)"
                + " VALUES (?, ?, ?, ?) ON CONFLICT (project_id, key) DO NOTHING",
            key.projectId(),
            key.value(),
            endpoint,
            Timestamp.from(now))
        == 1;
  }

  @Override
  public Stored load(IdempotencyKey key) {
    // queryForObject wirft bei fehlender Zeile selbst; null käme nur aus dem RowMapper, der keins
    // liefert. requireNonNull macht das für NullAway ausdrücklich.
    return Objects.requireNonNull(
        jdbc.queryForObject(
            "SELECT endpoint, response_status, response_body FROM idempotency_record"
                + " WHERE project_id = ? AND key = ?",
            (rs, n) ->
                new Stored(
                    rs.getString("endpoint"),
                    rs.getInt("response_status"),
                    rs.getString("response_body")),
            key.projectId(),
            key.value()));
  }

  @Override
  public void complete(IdempotencyKey key, int status, @Nullable Object body) {
    jdbc.update(
        "UPDATE idempotency_record SET response_status = ?, response_body = ?"
            + " WHERE project_id = ? AND key = ?",
        status,
        body == null ? null : encode(body),
        key.projectId(),
        key.value());
  }

  @Override
  public <T> T decode(IdempotencyKey key, String body, Class<T> type) {
    try {
      return json.readValue(body, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          "Abgelegte Antwort zu Idempotency-Key '%s' ist nicht lesbar".formatted(key.value()), e);
    }
  }

  @Override
  public int deleteOlderThan(Instant cutoff) {
    return jdbc.update(
        "DELETE FROM idempotency_record WHERE created_at < ?", Timestamp.from(cutoff));
  }

  private String encode(Object body) {
    try {
      return json.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Antwort ist nicht als JSON ablegbar", e);
    }
  }
}
