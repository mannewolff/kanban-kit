package org.mwolff.manban.nightrun.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.application.NightRunRepository;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

/**
 * Adapter des {@link NightRunRepository}-Ports (Issue #721).
 *
 * <p>Zweigeteilt aus einem Grund: Der Lesepfad ist gewöhnliches Spring Data JPA, der Schreibpfad
 * nicht. {@code INSERT … ON CONFLICT … RETURNING id} gibt es im Bestand nicht als
 * nicht-{@code @Modifying}-JPA-Query — {@code OutboxJpaRepository#insertIfAbsent} liefert einen
 * Rowcount, und der trägt hier nicht: Die Arbeitspakete brauchen die vergebene ID als
 * Fremdschlüssel. Der Schreibpfad läuft deshalb über {@link NamedParameterJdbcTemplate} nach dem
 * Vorbild {@code JdbcCardLabelRepository#addLabel}.
 */
@Component
// Die Kopplung folgt den Spalten: Der Adapter uebersetzt zwischen Domaenentypen, Entities
// und JDBC-Typen, und jede neue Spalte bringt ihren Typ mit. Mit Issue #944 sind es
// BigDecimal und NightRunOrigin mehr; 21 statt 20. Eine Aufteilung verteilte das Mapping
// einer Tabelle auf zwei Klassen.
@SuppressWarnings("PMD.CouplingBetweenObjects")
class NightRunRepositoryAdapter implements NightRunRepository {

  /** Name des benannten SQL-Parameters für die Projekt-ID (Sonar java:S1192). */
  private static final String P_PROJECT_ID = "projectId";

  /** Name des benannten SQL-Parameters für die Lauf-ID (Sonar java:S1192). */
  private static final String P_NIGHT_RUN_ID = "nightRunId";

  /** Spaltenname der Fehlerklasse in der Zählabfrage (Sonar java:S1192). */
  private static final String C_ERROR_CLASS = "error_class";

  private static final String INSERT_RUN =
      "INSERT INTO night_run (project_id, started_at, mode, duration_ms, processed_count,"
          + " skipped_count, unparsed_count, unparsed_sample, created_at, origin,"
          + " token_name, complete, updated_at, cost_usd, input_tokens, output_tokens,"
          + " cached_input_tokens)"
          + " VALUES (:projectId, :startedAt, :mode, :durationMs, :processedCount,"
          + " :skippedCount, :unparsedCount, :unparsedSample, :createdAt, :origin,"
          + " :tokenName, :complete, :updatedAt, :costUsd, :inputTokens, :outputTokens,"
          + " :cachedInputTokens)"
          + " ON CONFLICT (project_id, started_at) DO NOTHING"
          + " RETURNING id";

  private static final String INSERT_ITEM =
      "INSERT INTO night_run_item (night_run_id, card_number, title, state, error_class,"
          + " duration_ms, commit_hash, excerpt, cost_usd, input_tokens, output_tokens,"
          + " cached_input_tokens)"
          + " VALUES (:nightRunId, :cardNumber, :title, :state, :errorClass,"
          + " :durationMs, :commitHash, :excerpt, :costUsd, :inputTokens, :outputTokens,"
          + " :cachedInputTokens)";

  /**
   * Verdrängung des Ringpuffers: alles außerhalb der {@code keep} jüngsten Läufe fällt weg. Die
   * Auswahl steht als Unterabfrage, weil {@code LIMIT} weder in JPQL noch in einer {@code
   * DELETE}-Bedingung direkt zur Verfügung steht.
   */
  private static final String DELETE_OLDER =
      "DELETE FROM night_run WHERE project_id = :projectId AND id NOT IN"
          + " (SELECT id FROM night_run WHERE project_id = :projectId"
          + " ORDER BY started_at DESC, id DESC LIMIT :keep)";

  /** Ein Lauf zählt je Fehlerklasse höchstens einmal — daher {@code count(DISTINCT …)}. */
  private static final String COUNT_BY_ERROR_CLASS =
      "SELECT i.error_class AS error_class, count(DISTINCT i.night_run_id) AS runs"
          + " FROM night_run_item i JOIN night_run r ON r.id = i.night_run_id"
          + " WHERE r.project_id = :projectId AND i.error_class IS NOT NULL"
          + " GROUP BY i.error_class";

  private final NamedParameterJdbcTemplate jdbc;
  private final NightRunJpaRepository runs;
  private final NightRunItemJpaRepository items;

  NightRunRepositoryAdapter(
      NamedParameterJdbcTemplate jdbc,
      NightRunJpaRepository runs,
      NightRunItemJpaRepository items) {
    this.jdbc = jdbc;
    this.runs = runs;
    this.items = items;
  }

  @Override
  public Optional<Long> insertIfAbsent(NightRun run, List<NightRunItem> newItems) {
    // Leere Liste heisst: Der Lauf lag schon vor — DO NOTHING liefert dann keine Zeile.
    List<Long> vergebeneId = jdbc.queryForList(INSERT_RUN, runParameters(run), Long.class);
    if (vergebeneId.isEmpty()) {
      return Optional.empty();
    }
    Long runId = vergebeneId.get(0);
    insertItems(runId, newItems);
    return Optional.of(runId);
  }

  private void insertItems(Long runId, List<NightRunItem> newItems) {
    if (newItems.isEmpty()) {
      return;
    }
    SqlParameterSource[] batch =
        newItems.stream()
            .map(item -> item.withNightRunId(runId))
            .map(NightRunRepositoryAdapter::itemParameters)
            .toArray(SqlParameterSource[]::new);
    jdbc.batchUpdate(INSERT_ITEM, batch);
  }

  @Override
  public List<NightRun> findByProjectOrderByStartedAtDesc(long projectId) {
    return runs.findByProjectIdOrderByStartedAtDescIdDesc(projectId).stream()
        .map(NightRunRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<NightRunItem> findItemsByRunIds(Collection<Long> runIds) {
    if (runIds.isEmpty()) {
      return List.of();
    }
    return items.findByNightRunIdInOrderByNightRunIdAscIdAsc(runIds).stream()
        .map(NightRunRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public int deleteOlderThanNewest(long projectId, int keep) {
    return jdbc.update(
        DELETE_OLDER,
        new MapSqlParameterSource().addValue(P_PROJECT_ID, projectId).addValue("keep", keep));
  }

  @Override
  public Map<NightRunErrorClass, Long> countRunsByErrorClass(long projectId) {
    Map<NightRunErrorClass, Long> counts = new EnumMap<>(NightRunErrorClass.class);
    jdbc.query(
        COUNT_BY_ERROR_CLASS,
        new MapSqlParameterSource(P_PROJECT_ID, projectId),
        (RowCallbackHandler)
            rs ->
                counts.put(
                    NightRunErrorClass.valueOf(rs.getString(C_ERROR_CLASS)), rs.getLong("runs")));
    return counts;
  }

  private static SqlParameterSource runParameters(NightRun run) {
    MapSqlParameterSource parameter =
        new MapSqlParameterSource()
            .addValue(P_PROJECT_ID, run.projectId())
            .addValue("startedAt", zeitpunkt(run.startedAt()))
            .addValue("mode", run.mode().name())
            .addValue("durationMs", run.durationMs())
            .addValue("processedCount", run.processedCount())
            .addValue("skippedCount", run.skippedCount())
            .addValue("unparsedCount", run.unparsedCount())
            .addValue("unparsedSample", run.unparsedSample(), Types.VARCHAR)
            .addValue("createdAt", zeitpunkt(run.createdAt()))
            .addValue("origin", run.origin().name())
            .addValue("tokenName", run.tokenName(), Types.VARCHAR)
            .addValue("complete", run.complete())
            .addValue(
                "updatedAt",
                run.updatedAt() == null ? null : zeitpunkt(run.updatedAt()),
                Types.TIMESTAMP_WITH_TIMEZONE);
    verbrauchSchreiben(parameter, run.usage());
    return parameter;
  }

  /**
   * Die vier Verbrauchswerte an denselben Namen fuer Lauf und Arbeitspaket — sie tragen dieselbe
   * Form, und ein fehlendes {@code usage} setzt alle vier auf {@code NULL} („nicht gemessen").
   */
  private static void verbrauchSchreiben(
      MapSqlParameterSource parameter, @Nullable NightRunUsage usage) {
    parameter
        .addValue("costUsd", usage == null ? null : usage.costUsd(), Types.NUMERIC)
        .addValue("inputTokens", usage == null ? null : usage.inputTokens(), Types.BIGINT)
        .addValue("outputTokens", usage == null ? null : usage.outputTokens(), Types.BIGINT)
        .addValue(
            "cachedInputTokens", usage == null ? null : usage.cachedInputTokens(), Types.BIGINT);
  }

  private static SqlParameterSource itemParameters(NightRunItem item) {
    NightRunErrorClass errorClass = item.errorClass();
    MapSqlParameterSource parameter =
        new MapSqlParameterSource()
            .addValue(P_NIGHT_RUN_ID, item.nightRunId())
            .addValue("cardNumber", item.cardNumber())
            .addValue("title", item.title())
            .addValue("state", item.state().name())
            .addValue("errorClass", errorClass == null ? null : errorClass.name(), Types.VARCHAR)
            .addValue("durationMs", item.durationMs(), Types.BIGINT)
            .addValue("commitHash", item.commitHash(), Types.VARCHAR)
            .addValue("excerpt", item.excerpt(), Types.VARCHAR);
    verbrauchSchreiben(parameter, item.usage());
    return parameter;
  }

  /**
   * {@code timestamptz} bekommt einen {@link OffsetDateTime} statt eines {@code Instant}: Der
   * Postgres-Treiber bildet nur ersteren ohne Umweg ab. Über JPA (Lesepfad) übernimmt Hibernate die
   * Umrechnung selbst.
   */
  private static OffsetDateTime zeitpunkt(java.time.Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private static NightRun toDomain(NightRunEntity e) {
    return new NightRun(
        e.getId(),
        e.getProjectId(),
        e.getStartedAt(),
        NightRunMode.valueOf(e.getMode()),
        e.getDurationMs(),
        e.getProcessedCount(),
        e.getSkippedCount(),
        e.getUnparsedCount(),
        e.getUnparsedSample(),
        e.getCreatedAt(),
        NightRunOrigin.valueOf(e.getOrigin()),
        e.getTokenName(),
        e.isComplete(),
        e.getUpdatedAt(),
        verbrauchLesen(
            e.getCostUsd(), e.getInputTokens(), e.getOutputTokens(), e.getCachedInputTokens()));
  }

  private static NightRunItem toDomain(NightRunItemEntity e) {
    String errorClass = e.getErrorClass();
    return new NightRunItem(
        e.getId(),
        e.getNightRunId(),
        e.getCardNumber(),
        e.getTitle(),
        NightRunState.valueOf(e.getState()),
        errorClass == null ? null : NightRunErrorClass.valueOf(errorClass),
        e.getDurationMs(),
        e.getCommitHash(),
        e.getExcerpt(),
        verbrauchLesen(
            e.getCostUsd(), e.getInputTokens(), e.getOutputTokens(), e.getCachedInputTokens()));
  }

  /**
   * Aus vier Spalten wird ein {@link NightRunUsage} — oder {@code null}, wenn keine davon gesetzt
   * ist. Ein Record aus lauter {@code null} waere von „nicht gemessen" nicht zu unterscheiden und
   * zwaenge jede Anzeigestelle zu einer zweiten Fallunterscheidung.
   */
  private static @Nullable NightRunUsage verbrauchLesen(
      @Nullable BigDecimal costUsd,
      @Nullable Long inputTokens,
      @Nullable Long outputTokens,
      @Nullable Long cachedInputTokens) {
    if (costUsd == null
        && inputTokens == null
        && outputTokens == null
        && cachedInputTokens == null) {
      return null;
    }
    return new NightRunUsage(costUsd, inputTokens, outputTokens, cachedInputTokens);
  }
}
