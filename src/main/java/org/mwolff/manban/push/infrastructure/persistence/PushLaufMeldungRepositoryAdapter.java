package org.mwolff.manban.push.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import org.mwolff.manban.push.application.PushLaufMeldungRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapter des {@link PushLaufMeldungRepository}-Ports auf die Tabelle {@code push_lauf_gemeldet}.
 *
 * <p>JDBC statt JPA: Die Meldung ist eine einzige Einfügung mit {@code ON CONFLICT DO NOTHING}, und
 * genau sie macht „höchstens eine Nachricht je Lauf“ auch bei gleichzeitigen Meldejobs sicher. Eine
 * Entität brächte dafür nichts — gelesen wird die Zeile nie, sie besteht nur.
 */
@Component
class PushLaufMeldungRepositoryAdapter implements PushLaufMeldungRepository {

  private static final String EINFUEGEN =
      """
      INSERT INTO push_lauf_gemeldet (night_run_id, gemeldet_at, grund)
      VALUES (?, ?, ?)
      ON CONFLICT (night_run_id) DO NOTHING""";

  private final JdbcTemplate jdbc;

  PushLaufMeldungRepositoryAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean merke(long nightRunId, Instant gemeldetAt, String grund) {
    return jdbc.update(EINFUEGEN, nightRunId, Timestamp.from(gemeldetAt), grund) == 1;
  }
}
