package org.mwolff.manban.ratelimit.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Zugriff auf die Tabelle {@code overload_rejection} (Issue #1000, Plan #995 E14/E23).
 *
 * <p>Bewusst JDBC statt JPA: Die Tabelle wird nur fortgeschrieben und aufgeräumt, nie als Entität
 * geladen. {@code INSERT … ON CONFLICT DO UPDATE} ist in JPA nicht ausdrückbar, und genau diese
 * Anweisung trägt das Rennen zweier gleichzeitiger Erstschreiber derselben Stunde.
 */
@Component
public class OverloadRejectionTable {

  private static final String UPSERT =
      "INSERT INTO overload_rejection (user_id, hour_bucket, rejections) VALUES (?, ?, ?)"
          + " ON CONFLICT (user_id, hour_bucket)"
          + " DO UPDATE SET rejections = overload_rejection.rejections + EXCLUDED.rejections";

  private final JdbcTemplate jdbc;

  public OverloadRejectionTable(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Schlüssel einer Aggregatzeile.
   *
   * @param userId die abgewiesene Person
   * @param hourBucket Beginn der vollen Stunde, in der abgewiesen wurde
   */
  public record HourKey(long userId, Instant hourBucket) {}

  /**
   * Addiert die Zählungen auf ihre Zeilen — in <em>einem</em> Batch, gleich wie viele Personen und
   * Stunden er trägt. Fehlt eine Zeile, entsteht sie.
   */
  public void add(Map<HourKey, Integer> counts) {
    List<Object[]> rows =
        counts.entrySet().stream()
            .map(
                e ->
                    new Object[] {
                      e.getKey().userId(), Timestamp.from(e.getKey().hourBucket()), e.getValue()
                    })
            .toList();
    jdbc.batchUpdate(UPSERT, rows);
  }

  /** Löscht alle Zeilen, deren Stunde vor {@code cutoff} beginnt, und liefert ihre Zahl. */
  public int deleteOlderThan(Instant cutoff) {
    return jdbc.update(
        "DELETE FROM overload_rejection WHERE hour_bucket < ?", Timestamp.from(cutoff));
  }
}
