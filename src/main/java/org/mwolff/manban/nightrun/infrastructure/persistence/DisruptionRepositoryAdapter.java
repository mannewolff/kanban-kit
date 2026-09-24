package org.mwolff.manban.nightrun.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.application.DisruptionRepository;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapter des {@link DisruptionRepository}-Ports (Issue #1080).
 *
 * <p>Über {@link NamedParameterJdbcTemplate} wie der Schreibpfad des {@code
 * NightRunRepositoryAdapter}: Die Abfrage geht über vier Tabellen und braucht ein {@code LEFT JOIN
 * … IS NULL}, das sich als JPA-Query nur umständlich ausdrücken ließe.
 */
@Component
class DisruptionRepositoryAdapter implements DisruptionRepository {

  /**
   * Die Kandidaten, jüngster Lauf zuoberst.
   *
   * <p>Vier Bedingungen, alle in der Datenbank entscheidbar: die Gattung (eine interaktive Sitzung
   * ist kein Nachtlauf), der Abschluss (ein laufender Lauf hat noch keinen Ausgang), die Teilnahme
   * des Projekts und die fehlende Quittung. <b>Was eine Störung ist, steht hier nicht</b> — das
   * entscheidet {@code NightRunOutcome} in der Domäne, und zwar als einziger Ort.
   *
   * <p>Kein {@code LIMIT} (Plan #1072 E13): AK 4 verlangt jede offene Störung, und ein stilles
   * Limit sähe aus wie Vollständigkeit. Die Menge ist durch den Ringpuffer je Projekt nach oben
   * beschränkt.
   */
  private static final String KANDIDATEN =
      """
      SELECT r.id AS night_run_id, r.project_id, p.name AS project_name, r.mode,
             r.started_at, r.updated_at, r.complete, r.no_work_reason, r.abort_reason,
             r.closed_at
        FROM night_run r
        JOIN project p ON p.id = r.project_id
        LEFT JOIN night_run_disruption_ack a ON a.night_run_id = r.id
       WHERE r.kind = 'NIGHT'
         AND r.complete = true
         AND p.dashboard_participation = true
         AND a.night_run_id IS NULL
       ORDER BY r.started_at DESC, r.id DESC
      """;

  /**
   * Die Kandidaten der beiden Lauf-Bereiche, jüngster zuoberst.
   *
   * <p>Zwei Bedingungen weniger als bei den {@link #KANDIDATEN}: <b>kein</b> Filter auf den
   * Abschluss, weil die laufenden Läufe gerade der obere Bereich sind, und <b>kein</b> Ausschluss
   * quittierter Läufe, weil das Quittieren den Ausgang nicht ändert.
   *
   * <p><b>Zwei Zweige unter einem {@code OR}</b> (Issue #1109):
   *
   * <ul>
   *   <li><b>Zur Nacht gehörig:</b> Der Startzeitpunkt liegt zwischen {@code :from} einschließlich
   *       und {@code :to} ausschließlich.
   *   <li><b>Noch am Leben:</b> {@code complete = false} und das letzte Lebenszeichen ist nicht
   *       älter als {@code :lebenszeichenAb} — <em>ohne</em> Blick auf den Start. Ohne diesen Zweig
   *       verschwände ein Lauf, der um 10:27 begann und über Mittag arbeitet, für seine ganze
   *       Restlaufzeit vom Leitstand: Die neue Nacht kennt ihn nicht, und beendet ist er auch nicht
   *       (#1086 AK 1 kennt für die laufenden Läufe keine Nachtgrenze).
   * </ul>
   *
   * <p><b>Gattung und Teilnahme stehen vor der Klammer</b> — sie gelten beiden Zweigen: Eine
   * interaktive Sitzung ist auch dann kein Nachtlauf, wenn sie gerade arbeitet.
   *
   * <p>Das {@code COALESCE} ist dieselbe Rückfallregel, die {@code NightRunOutcome} trägt: Ohne
   * {@code updated_at} ist der Start das einzige Lebenszeichen (der Upload-Weg schreibt einen Lauf
   * nie fort). Und {@code >=} ist derselbe Rand — genau <em>auf</em> der Frist lebt der Lauf noch.
   * Die Abfrage filtert damit nur vor; entschieden wird der Ausgang weiter in der Domäne.
   *
   * <p>Kein {@code LIMIT}, aus demselben Grund wie oben; Nachtspanne und Stillefrist begrenzen die
   * Menge ohnehin schärfer als der Ringpuffer.
   */
  private static final String LAEUFE_DER_NACHT =
      """
      SELECT r.id AS night_run_id, r.project_id, p.name AS project_name, r.mode,
             r.started_at, r.updated_at, r.complete, r.no_work_reason, r.abort_reason,
             r.closed_at
        FROM night_run r
        JOIN project p ON p.id = r.project_id
       WHERE r.kind = 'NIGHT'
         AND p.dashboard_participation = true
         AND ( (r.started_at >= :from AND r.started_at < :to)
               OR (r.complete = false
                   AND COALESCE(r.updated_at, r.started_at) >= :lebenszeichenAb) )
       ORDER BY r.started_at DESC, r.id DESC
      """;

  /**
   * Ein einzelner Lauf in der Form der beiden Listen-Abfragen (Issue #1197).
   *
   * <p>Dieselben Spalten und dieselbe Teilnahme-Bedingung wie oben — nur auf eine Kennung
   * eingegrenzt. <b>Ohne</b> Gattungsfilter: Eine interaktive Sitzung steht gar nicht erst auf dem
   * Leitstand, und der Ausgang weist sie hier ohnehin ab.
   */
  private static final String EIN_LAUF =
      """
      SELECT r.id AS night_run_id, r.project_id, p.name AS project_name, r.mode,
             r.started_at, r.updated_at, r.complete, r.no_work_reason, r.abort_reason,
             r.closed_at
        FROM night_run r
        JOIN project p ON p.id = r.project_id
       WHERE r.id = :nightRunId
         AND p.dashboard_participation = true
      """;

  /**
   * Die Kennzeichnung von Hand (Issue #1197).
   *
   * <p>Die Bedingung {@code closed_at IS NULL} ist die Idempotenz — dieselbe Zusage, die beim
   * Quittieren {@code ON CONFLICT DO NOTHING} trägt: Der erste Kennzeichnende bleibt vermerkt.
   */
  private static final String KENNZEICHNUNG =
      """
      UPDATE night_run
         SET closed_at = :at, closed_by = :userId
       WHERE id = :nightRunId
         AND closed_at IS NULL
      """;

  /** Lauf und Teilnahme in einer Abfrage — beide Verneinungen enden beim Aufrufer als 404. */
  private static final String ACK_ZIEL =
      """
      SELECT r.id AS night_run_id, r.project_id
        FROM night_run r
        JOIN project p ON p.id = r.project_id
       WHERE r.id = :nightRunId
         AND p.dashboard_participation = true
      """;

  /**
   * {@code ON CONFLICT DO NOTHING} ist die Idempotenz (AK 8): Der erste Quittierende bleibt
   * vermerkt, der zweite Aufruf ist ein stiller No-Op statt eines Constraint-Fehlers.
   */
  private static final String QUITTUNG =
      """
      INSERT INTO night_run_disruption_ack (night_run_id, acknowledged_by, acknowledged_at)
      VALUES (:nightRunId, :userId, :at)
      ON CONFLICT (night_run_id) DO NOTHING
      """;

  /** Beide Abfragen liefern dieselben Spalten — ein Mapper, damit sie nicht auseinanderlaufen. */
  private static final RowMapper<DisruptionCandidate> KANDIDAT =
      (rs, zeile) ->
          new DisruptionCandidate(
              rs.getLong("night_run_id"),
              rs.getLong("project_id"),
              rs.getString("project_name"),
              NightRunMode.valueOf(rs.getString("mode")),
              rs.getObject("started_at", OffsetDateTime.class).toInstant(),
              zeitpunkt(rs, "updated_at"),
              rs.getBoolean("complete"),
              rs.getString("no_work_reason"),
              rs.getString("abort_reason"),
              zeitpunkt(rs, "closed_at"));

  /**
   * Ein Zeitpunkt, der fehlen darf — {@code null} bleibt {@code null}.
   *
   * <p>Eigene Methode seit Issue #1197: Der Mapper führt mit {@code closed_at} zwei solche Spalten,
   * und zweimal derselbe Dreisatz lief beim nächsten Feld auseinander.
   */
  @Nullable
  private static Instant zeitpunkt(ResultSet rs, String spalte) throws SQLException {
    OffsetDateTime wert = rs.getObject(spalte, OffsetDateTime.class);
    return wert == null ? null : wert.toInstant();
  }

  private final NamedParameterJdbcTemplate jdbc;

  DisruptionRepositoryAdapter(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<DisruptionCandidate> openCandidates() {
    return jdbc.query(KANDIDATEN, new MapSqlParameterSource(), KANDIDAT);
  }

  @Override
  public List<DisruptionCandidate> candidatesOfNight(
      Instant from, Instant to, Instant jetzt, Duration stilleFrist) {
    return jdbc.query(
        LAEUFE_DER_NACHT,
        new MapSqlParameterSource()
            .addValue("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
            .addValue("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC))
            .addValue(
                "lebenszeichenAb",
                OffsetDateTime.ofInstant(jetzt.minus(stilleFrist), ZoneOffset.UTC)),
        KANDIDAT);
  }

  @Override
  public Optional<AckTarget> ackTarget(long nightRunId) {
    return jdbc
        .query(
            ACK_ZIEL,
            new MapSqlParameterSource("nightRunId", nightRunId),
            (rs, zeile) -> new AckTarget(rs.getLong("night_run_id"), rs.getLong("project_id")))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<DisruptionCandidate> candidate(long nightRunId) {
    return jdbc
        .query(EIN_LAUF, new MapSqlParameterSource("nightRunId", nightRunId), KANDIDAT)
        .stream()
        .findFirst();
  }

  @Override
  public void close(long nightRunId, long userId, Instant at) {
    jdbc.update(
        KENNZEICHNUNG,
        new MapSqlParameterSource()
            .addValue("nightRunId", nightRunId)
            .addValue("userId", userId)
            .addValue("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC)));
  }

  @Override
  public void acknowledge(long nightRunId, long userId, Instant at) {
    jdbc.update(
        QUITTUNG,
        new MapSqlParameterSource()
            .addValue("nightRunId", nightRunId)
            .addValue("userId", userId)
            .addValue("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC)));
  }
}
