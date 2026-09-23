package org.mwolff.manban.nightrun.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.application.NightRunRepository;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunBudget;
import org.mwolff.manban.nightrun.domain.NightRunBudgetOrigin;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunItemStage;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunStage;
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
// PMD.CouplingBetweenObjects: Die Kopplung folgt den Spalten: Der Adapter uebersetzt zwischen
// Domaenentypen, Entities und JDBC-Typen, und jede neue Spalte bringt ihren Typ mit. Mit Issue
// #944 sind es BigDecimal und NightRunOrigin mehr, mit Issue #1010 NightRunKind, mit Issue #1112
// NightRunBudget, NightRunBudgetOrigin, NightRunStage und NightRunItemStage. Eine Aufteilung
// verteilte das Mapping einer Tabelle auf zwei Klassen.
// PMD.GodClass: dieselbe Ursache, nur anders gezaehlt — WMC und ATFD summieren die je fuer sich
// trivialen addValue-/getX-Zeilen des Mappings, die TCC ist niedrig, weil Schreib- und Lesepfad
// derselben Tabelle sich keine Felder teilen. Das ist die Form eines Persistenz-Adapters und kein
// Smell. Wenn hier weiter waechst, ist die Tabelle zu zerlegen — nicht diese Klasse.
@SuppressWarnings({"PMD.CouplingBetweenObjects", "PMD.GodClass"})
class NightRunRepositoryAdapter implements NightRunRepository {

  /** Name des benannten SQL-Parameters für die Projekt-ID (Sonar java:S1192). */
  private static final String P_PROJECT_ID = "projectId";

  /** Name des benannten SQL-Parameters für die Lauf-ID (Sonar java:S1192). */
  private static final String P_NIGHT_RUN_ID = "nightRunId";

  /** Name des benannten SQL-Parameters für den Beginn des Laufs (Sonar java:S1192). */
  private static final String P_STARTED_AT = "startedAt";

  /** Name des benannten SQL-Parameters für die Gattung (Sonar java:S1192). */
  private static final String P_KIND = "kind";

  /** Spaltenname der Fehlerklasse in der Zählabfrage (Sonar java:S1192). */
  private static final String C_ERROR_CLASS = "error_class";

  /**
   * Der Platzhalter für „keine Vorgaben gemeldet" (Issue #1112). Er steht hier und nicht in der
   * Domäne: Fachlich gibt es ihn nicht — ein Lauf ohne Vorgaben trägt {@code null} —, er ist allein
   * die Schreibform davon, damit {@link #budgetSchreiben} die Fallunterscheidung einmal trifft
   * statt je Spalte.
   */
  private static final NightRunBudget OHNE_VORGABEN =
      new NightRunBudget(null, null, null, null, null, null, List.of());

  private static final String INSERT_RUN =
      "INSERT INTO night_run (project_id, started_at, mode, kind, duration_ms, processed_count,"
          + " skipped_count, unparsed_count, unparsed_sample, created_at, origin,"
          + " token_name, complete, updated_at, cost_usd, input_tokens, output_tokens,"
          + " cached_input_tokens, model_duration_ms, turns, no_work_reason,"
          + " budget_plan_min, budget_review_min, budget_pakete_min, budget_abdeckung_min,"
          + " budget_kosten_usd, budget_origin, budget_default_fields, abort_reason)"
          + " VALUES (:projectId, :startedAt, :mode, :kind, :durationMs, :processedCount,"
          + " :skippedCount, :unparsedCount, :unparsedSample, :createdAt, :origin,"
          + " :tokenName, :complete, :updatedAt, :costUsd, :inputTokens, :outputTokens,"
          + " :cachedInputTokens, :modelDurationMs, :turns, :noWorkReason,"
          + " :budgetPlanMin, :budgetReviewMin, :budgetPaketeMin, :budgetAbdeckungMin,"
          + " :budgetKostenUsd, :budgetOrigin, :budgetDefaultFields, :abortReason)"
          + " ON CONFLICT (project_id, started_at) DO NOTHING"
          + " RETURNING id";

  /**
   * Je Paket eine Anweisung mit {@code RETURNING id} statt eines Stapels über alle Pakete (Issue
   * #1112): Die Stufen brauchen die vergebene Paket-ID als Fremdschlüssel, und {@code
   * (night_run_id, card_number)} taugt dafür nicht — es ist kein Schlüssel, zwei Vorgänge eines
   * Laufs können dieselbe Karte betreffen.
   */
  private static final String INSERT_ITEM =
      "INSERT INTO night_run_item (night_run_id, project_id, started_at, mode, kind, card_number,"
          + " title, state, error_class, duration_ms, commit_hash, excerpt, cost_usd,"
          + " input_tokens, output_tokens, cached_input_tokens, model_duration_ms, turns)"
          + " VALUES (:nightRunId, :projectId, :startedAt, :mode, :kind, :cardNumber, :title,"
          + " :state, :errorClass,"
          + " :durationMs, :commitHash, :excerpt, :costUsd, :inputTokens, :outputTokens,"
          + " :cachedInputTokens, :modelDurationMs, :turns)"
          + " RETURNING id";

  private static final String INSERT_STAGE =
      "INSERT INTO night_run_item_stage (night_run_item_id, stage, duration_ms, cost_usd,"
          + " input_tokens, output_tokens, cached_input_tokens, model_duration_ms, turns)"
          + " VALUES (:nightRunItemId, :stage, :durationMs, :costUsd, :inputTokens,"
          + " :outputTokens, :cachedInputTokens, :modelDurationMs, :turns)";

  /**
   * Sperrt die Projektzeile für die Dauer der Transaktion (Issue #1090) — dieselbe Zeile und
   * dieselbe Form wie {@code CardRepositoryAdapter.lockCardNumbers}. Gelesen wird die Tabelle
   * {@code project} per SQL, wie es der {@code DisruptionRepositoryAdapter} schon tut; ein Import
   * aus dem Modul {@code project} entstünde dadurch nicht.
   */
  private static final String LOCK_PROJECT =
      "SELECT id FROM project WHERE id = :projectId FOR UPDATE";

  /**
   * Verdrängung des Ringpuffers: alles außerhalb der {@code keep} jüngsten Läufe <b>dieser
   * Gattung</b> fällt weg (je Gattung getrennt seit Issue #1011). Die Auswahl steht als
   * Unterabfrage, weil {@code LIMIT} weder in JPQL noch in einer {@code DELETE}-Bedingung direkt
   * zur Verfügung steht.
   */
  private static final String SELECT_ID_FOR_UPDATE =
      "SELECT id FROM night_run WHERE project_id = :projectId AND started_at = :startedAt"
          + " FOR UPDATE";

  private static final String UPDATE_RUN =
      "UPDATE night_run SET mode = :mode, kind = :kind, duration_ms = :durationMs,"
          + " processed_count = :processedCount, skipped_count = :skippedCount,"
          + " unparsed_count = :unparsedCount, unparsed_sample = :unparsedSample,"
          + " origin = :origin, token_name = :tokenName, complete = :complete,"
          + " updated_at = :updatedAt, cost_usd = :costUsd, input_tokens = :inputTokens,"
          + " output_tokens = :outputTokens, cached_input_tokens = :cachedInputTokens,"
          + " model_duration_ms = :modelDurationMs, turns = :turns,"
          + " no_work_reason = :noWorkReason,"
          + " budget_plan_min = :budgetPlanMin, budget_review_min = :budgetReviewMin,"
          + " budget_pakete_min = :budgetPaketeMin, budget_abdeckung_min = :budgetAbdeckungMin,"
          + " budget_kosten_usd = :budgetKostenUsd, budget_origin = :budgetOrigin,"
          + " budget_default_fields = :budgetDefaultFields,"
          // Auch der Abbruchgrund wird ersetzt und nicht nur gesetzt (Issue #1142): Eine Meldung
          // ist der vollstaendige Stand des Laufs, und ein spaeterer Stand ohne Abbruch raeumt
          // einen frueher gemeldeten Grund wieder ab.
          + " abort_reason = :abortReason"
          + " WHERE id = :id";

  private static final String DELETE_ITEMS_OF_RUN =
      "DELETE FROM night_run_item WHERE night_run_id = :nightRunId";

  private static final String DELETE_OLDER =
      "DELETE FROM night_run WHERE project_id = :projectId AND kind = :kind AND id NOT IN"
          + " (SELECT id FROM night_run WHERE project_id = :projectId AND kind = :kind"
          + " ORDER BY started_at DESC, id DESC LIMIT :keep)";

  /**
   * Ein Lauf zählt je Fehlerklasse höchstens einmal — daher {@code count(DISTINCT …)}. Gefiltert
   * wird am Lauf und nicht am Paket (Issue #1012): Die Gattung steht an beiden, aber der Lauf ist
   * das, was gezählt wird.
   */
  private static final String COUNT_BY_ERROR_CLASS =
      "SELECT i.error_class AS error_class, count(DISTINCT i.night_run_id) AS runs"
          + " FROM night_run_item i JOIN night_run r ON r.id = i.night_run_id"
          + " WHERE r.project_id = :projectId AND r.kind = :kind AND i.error_class IS NOT NULL"
          + " GROUP BY i.error_class";

  private final NamedParameterJdbcTemplate jdbc;
  private final NightRunJpaRepository runs;
  private final NightRunItemJpaRepository items;
  private final NightRunItemStageJpaRepository stages;

  NightRunRepositoryAdapter(
      NamedParameterJdbcTemplate jdbc,
      NightRunJpaRepository runs,
      NightRunItemJpaRepository items,
      NightRunItemStageJpaRepository stages) {
    this.jdbc = jdbc;
    this.runs = runs;
    this.items = items;
    this.stages = stages;
  }

  @Override
  public void lockProject(long projectId) {
    jdbc.queryForList(
        LOCK_PROJECT, new MapSqlParameterSource().addValue(P_PROJECT_ID, projectId), Long.class);
  }

  @Override
  public Optional<Long> insertIfAbsent(NightRun run, List<NightRunItem> newItems) {
    // Leere Liste heisst: Der Lauf lag schon vor — DO NOTHING liefert dann keine Zeile.
    List<Long> vergebeneId = jdbc.queryForList(INSERT_RUN, runParameters(run), Long.class);
    if (vergebeneId.isEmpty()) {
      return Optional.empty();
    }
    Long runId = vergebeneId.get(0);
    insertItems(run, runId, newItems);
    return Optional.of(runId);
  }

  /**
   * Zwei Anweisungen statt {@code ON CONFLICT ... DO UPDATE}: Das Ersetzen braucht ohnehin mehr als
   * eine — die Arbeitspakete werden gelöscht und neu geschrieben —, und {@code xmax = 0} als
   * Unterscheidung zwischen „angelegt" und „ersetzt" wäre ein Trick, dessen Bedeutung kein Leser
   * der Zeile ansieht.
   *
   * <p>Das {@code FOR UPDATE} sperrt die Zeile für die Dauer der Transaktion. Ohne es könnten zwei
   * gleichzeitige Meldungen desselben Laufs beide kein Vorkommen sehen und beide einfügen wollen;
   * die zweite liefe in den eindeutigen Schlüssel. Es sperrt allerdings nur <b>diesen</b> Lauf —
   * die Serialisierung über Läufe hinweg, die der Ringpuffer braucht, trägt seit Issue #1090 die
   * Projektsperre aus {@link #lockProject}, die jedem Schreibweg vorausgeht.
   */
  @Override
  public UpsertResult upsert(NightRun run, List<NightRunItem> newItems) {
    SqlParameterSource schluessel =
        new MapSqlParameterSource()
            .addValue(P_PROJECT_ID, run.projectId())
            .addValue(P_STARTED_AT, zeitpunkt(run.startedAt()));
    List<Long> vorhanden = jdbc.queryForList(SELECT_ID_FOR_UPDATE, schluessel, Long.class);

    if (vorhanden.isEmpty()) {
      Long runId = jdbc.queryForList(INSERT_RUN, runParameters(run), Long.class).get(0);
      insertItems(run, runId, newItems);
      return new UpsertResult(runId, true);
    }

    Long runId = vorhanden.get(0);
    MapSqlParameterSource aenderung = (MapSqlParameterSource) runParameters(run);
    aenderung.addValue("id", runId);
    jdbc.update(UPDATE_RUN, aenderung);
    jdbc.update(DELETE_ITEMS_OF_RUN, new MapSqlParameterSource().addValue(P_NIGHT_RUN_ID, runId));
    insertItems(run, runId, newItems);
    return new UpsertResult(runId, false);
  }

  /**
   * Projekt, Startzeitpunkt, Lauf-Art und Gattung eines Pakets kommen aus dem Lauf und nie aus dem
   * Paket (Issue #964, um die Gattung erweitert in #1010): So kann kein Paket mit einem anderen
   * Projekt geschrieben werden als sein Lauf — ein verwaistes Paket fände man sonst später im
   * falschen Projekt wieder.
   *
   * <p>Je Paket eine Anweisung statt eines Stapels über alle (Issue #1112): Die Stufen hängen an
   * der Paket-ID, und die gibt erst das {@code RETURNING id} des einzelnen {@code INSERT} her. Der
   * Stapel bleibt dort, wo er trägt — bei den Stufen eines Pakets.
   */
  private void insertItems(NightRun run, Long runId, List<NightRunItem> newItems) {
    for (NightRunItem item : newItems) {
      Long itemId =
          jdbc.queryForObject(
              INSERT_ITEM, itemParameters(item.withNightRunId(runId), run), Long.class);
      insertStages(itemId, item.stages());
    }
  }

  /**
   * Die Stufen eines Pakets als Stapel. Beim Ersetzen eines Laufs fallen sie mit ihren Paketen über
   * {@code ON DELETE CASCADE} und werden hier neu geschrieben — ersetzt, nicht ergänzt.
   */
  private void insertStages(@Nullable Long itemId, List<NightRunItemStage> stages) {
    if (stages.isEmpty()) {
      return;
    }
    SqlParameterSource[] batch =
        stages.stream()
            .map(stage -> stageParameters(itemId, stage))
            .toArray(SqlParameterSource[]::new);
    jdbc.batchUpdate(INSERT_STAGE, batch);
  }

  @Override
  public List<NightRun> findByProjectAndKindOrderByStartedAtDesc(
      long projectId, NightRunKind kind) {
    return runs.findByProjectIdAndKindOrderByStartedAtDescIdDesc(projectId, kind.name()).stream()
        .map(NightRunRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<NightRunItem> findItemsByRunIds(Collection<Long> runIds) {
    if (runIds.isEmpty()) {
      return List.of();
    }
    return mitStufen(items.findByNightRunIdInOrderByNightRunIdAscIdAsc(runIds));
  }

  @Override
  public List<NightRunItem> findByCard(long projectId, int cardNumber) {
    return mitStufen(
        items.findByProjectIdAndCardNumberOrderByStartedAtDescIdDesc(projectId, cardNumber));
  }

  /**
   * Die Stufen werden zu allen Paketen in einer zweiten Abfrage nachgeladen (Issue #1112) und nicht
   * je Paket. Ohne das Nachladen behauptete die leere Liste „hatte keine Stufen" auch dort, wo es
   * welche gibt — und an den Anläufen einer Karte fiele es niemandem auf.
   */
  private List<NightRunItem> mitStufen(List<NightRunItemEntity> gefunden) {
    if (gefunden.isEmpty()) {
      return List.of();
    }
    // requireNonNull statt einer Abfrage: Eine gelesene Zeile hat ihre ID: @Nullable steht am
    // Getter nur, weil dieselbe Entity vor dem Einfuegen noch keine haette — und diese Entities
    // werden nie eingefuegt. Eine Abfrage waere ein Zweig, den kein Fall erreichen kann.
    List<Long> itemIds = gefunden.stream().map(e -> Objects.requireNonNull(e.getId())).toList();
    Map<Long, List<NightRunItemStage>> jeItem =
        stages.findByNightRunItemIdInOrderByNightRunItemIdAscIdAsc(itemIds).stream()
            .collect(
                Collectors.groupingBy(
                    NightRunItemStageEntity::getNightRunItemId,
                    Collectors.mapping(NightRunRepositoryAdapter::toDomain, Collectors.toList())));
    return gefunden.stream()
        .map(e -> toDomain(e, jeItem.getOrDefault(e.getId(), List.of())))
        .toList();
  }

  @Override
  public int deleteOlderThanNewest(long projectId, NightRunKind kind, int keep) {
    return jdbc.update(
        DELETE_OLDER,
        new MapSqlParameterSource()
            .addValue(P_PROJECT_ID, projectId)
            .addValue(P_KIND, kind.name())
            .addValue("keep", keep));
  }

  @Override
  public int deleteOrphanItemsOfRun(long projectId, java.time.Instant startedAt) {
    return items.deleteOrphansOfRun(projectId, startedAt);
  }

  /**
   * Die Gattung geht als {@link String} in die native Abfrage: Die Spalte ist ein {@code varchar} +
   * {@code CHECK} (V34), und ein Enum-Parameter bände Hibernate sonst als Ordinalzahl.
   */
  @Override
  public int deleteOrphanItemsOlderThanNewest(long projectId, NightRunKind kind, int keep) {
    return items.deleteOrphansOlderThanNewest(projectId, kind.name(), keep);
  }

  @Override
  public Map<NightRunErrorClass, Long> countRunsByErrorClass(long projectId, NightRunKind kind) {
    Map<NightRunErrorClass, Long> counts = new EnumMap<>(NightRunErrorClass.class);
    jdbc.query(
        COUNT_BY_ERROR_CLASS,
        new MapSqlParameterSource(P_PROJECT_ID, projectId).addValue(P_KIND, kind.name()),
        (RowCallbackHandler)
            rs ->
                counts.put(
                    NightRunErrorClass.valueOf(rs.getString(C_ERROR_CLASS)), rs.getLong("runs")));
    return counts;
  }

  private static SqlParameterSource runParameters(NightRun run) {
    // Einmal geholt statt zweimal gerufen: Beim doppelten Getter-Aufruf sieht Sonar (java:S4449)
    // einen Pfad, auf dem der zweite Aufruf null liefern koennte, obwohl der erste es nicht tat.
    Instant updatedAt = run.updatedAt();
    MapSqlParameterSource parameter =
        new MapSqlParameterSource()
            .addValue(P_PROJECT_ID, run.projectId())
            .addValue(P_STARTED_AT, zeitpunkt(run.startedAt()))
            .addValue("mode", run.mode().name())
            .addValue(P_KIND, run.kind().name())
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
                updatedAt == null ? null : zeitpunkt(updatedAt),
                Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("noWorkReason", run.noWorkReason(), Types.VARCHAR)
            .addValue("abortReason", run.abortReason(), Types.VARCHAR);
    verbrauchSchreiben(parameter, run.usage());
    budgetSchreiben(parameter, run.budget());
    return parameter;
  }

  /**
   * Die sechs Verbrauchswerte an denselben Namen fuer Lauf, Arbeitspaket und Stufe — sie tragen
   * dieselbe Form, und ein fehlendes {@code usage} setzt alle sechs auf {@code NULL} („nicht
   * gemessen").
   */
  private static void verbrauchSchreiben(
      MapSqlParameterSource parameter, @Nullable NightRunUsage usage) {
    parameter
        .addValue("costUsd", usage == null ? null : usage.costUsd(), Types.NUMERIC)
        .addValue("inputTokens", usage == null ? null : usage.inputTokens(), Types.BIGINT)
        .addValue("outputTokens", usage == null ? null : usage.outputTokens(), Types.BIGINT)
        .addValue(
            "cachedInputTokens", usage == null ? null : usage.cachedInputTokens(), Types.BIGINT)
        .addValue("modelDurationMs", usage == null ? null : usage.modelDurationMs(), Types.BIGINT)
        .addValue("turns", usage == null ? null : usage.turns(), Types.INTEGER);
  }

  /**
   * Die sieben Budget-Spalten des Laufs (Issue #1112). Ein fehlendes Budget setzt alle auf {@code
   * NULL} — „nicht angegeben". Die Feldliste geht als kommagetrennte Zeichenkette in eine Spalte;
   * bei {@link NightRunBudgetOrigin#CONFIGURED} und ohne Budget bleibt sie {@code NULL}.
   */
  private static void budgetSchreiben(
      MapSqlParameterSource parameter, @Nullable NightRunBudget budget) {
    // Ein Mal die Fallunterscheidung statt sieben Mal: Der Platzhalter traegt in jedem Feld
    // genau das, was ein fehlendes Budget in die Spalte schreiben soll.
    NightRunBudget vorgaben = budget == null ? OHNE_VORGABEN : budget;
    NightRunBudgetOrigin origin = vorgaben.origin();
    List<String> felder = vorgaben.defaultFields();
    parameter
        .addValue("budgetPlanMin", vorgaben.planMin(), Types.INTEGER)
        .addValue("budgetReviewMin", vorgaben.reviewMin(), Types.INTEGER)
        .addValue("budgetPaketeMin", vorgaben.paketeMin(), Types.INTEGER)
        .addValue("budgetAbdeckungMin", vorgaben.abdeckungMin(), Types.INTEGER)
        .addValue("budgetKostenUsd", vorgaben.kostenUsd(), Types.NUMERIC)
        .addValue("budgetOrigin", origin == null ? null : origin.name(), Types.VARCHAR)
        .addValue(
            "budgetDefaultFields",
            felder.isEmpty() ? null : String.join(",", felder),
            Types.VARCHAR);
  }

  private static SqlParameterSource stageParameters(
      @Nullable Long itemId, NightRunItemStage stage) {
    MapSqlParameterSource parameter =
        new MapSqlParameterSource()
            .addValue("nightRunItemId", itemId)
            .addValue("stage", stage.stage().name())
            .addValue("durationMs", stage.durationMs(), Types.BIGINT);
    verbrauchSchreiben(parameter, stage.usage());
    return parameter;
  }

  private static SqlParameterSource itemParameters(NightRunItem item, NightRun run) {
    NightRunErrorClass errorClass = item.errorClass();
    MapSqlParameterSource parameter =
        new MapSqlParameterSource()
            .addValue(P_NIGHT_RUN_ID, item.nightRunId())
            .addValue(P_PROJECT_ID, run.projectId())
            .addValue(P_STARTED_AT, zeitpunkt(run.startedAt()))
            .addValue("mode", run.mode().name())
            .addValue(P_KIND, run.kind().name())
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
  private static OffsetDateTime zeitpunkt(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private static NightRun toDomain(NightRunEntity e) {
    return new NightRun(
        e.getId(),
        e.getProjectId(),
        e.getStartedAt(),
        NightRunMode.valueOf(e.getMode()),
        NightRunKind.valueOf(e.getKind()),
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
            e.getCostUsd(),
            e.getInputTokens(),
            e.getOutputTokens(),
            e.getCachedInputTokens(),
            e.getModelDurationMs(),
            e.getTurns()),
        e.getNoWorkReason(),
        budgetLesen(e),
        e.getAbortReason());
  }

  private static NightRunItem toDomain(NightRunItemEntity e, List<NightRunItemStage> stages) {
    String errorClass = e.getErrorClass();
    return new NightRunItem(
        e.getId(),
        e.getNightRunId(),
        e.getProjectId(),
        e.getStartedAt(),
        NightRunMode.valueOf(e.getMode()),
        NightRunKind.valueOf(e.getKind()),
        e.getCardNumber(),
        e.getTitle(),
        NightRunState.valueOf(e.getState()),
        errorClass == null ? null : NightRunErrorClass.valueOf(errorClass),
        e.getDurationMs(),
        e.getCommitHash(),
        e.getExcerpt(),
        verbrauchLesen(
            e.getCostUsd(),
            e.getInputTokens(),
            e.getOutputTokens(),
            e.getCachedInputTokens(),
            e.getModelDurationMs(),
            e.getTurns()),
        stages);
  }

  private static NightRunItemStage toDomain(NightRunItemStageEntity e) {
    return new NightRunItemStage(
        NightRunStage.valueOf(e.getStage()),
        e.getDurationMs(),
        verbrauchLesen(
            e.getCostUsd(),
            e.getInputTokens(),
            e.getOutputTokens(),
            e.getCachedInputTokens(),
            e.getModelDurationMs(),
            e.getTurns()));
  }

  /**
   * Aus sechs Spalten wird ein {@link NightRunUsage} — oder {@code null}, wenn keine davon gesetzt
   * ist. Ein Record aus lauter {@code null} waere von „nicht gemessen" nicht zu unterscheiden und
   * zwaenge jede Anzeigestelle zu einer zweiten Fallunterscheidung.
   */
  private static @Nullable NightRunUsage verbrauchLesen(
      @Nullable BigDecimal costUsd,
      @Nullable Long inputTokens,
      @Nullable Long outputTokens,
      @Nullable Long cachedInputTokens,
      @Nullable Long modelDurationMs,
      @Nullable Integer turns) {
    if (costUsd == null
        && inputTokens == null
        && outputTokens == null
        && cachedInputTokens == null
        && modelDurationMs == null
        && turns == null) {
      return null;
    }
    return new NightRunUsage(
        costUsd, inputTokens, outputTokens, cachedInputTokens, modelDurationMs, turns);
  }

  /**
   * Aus den sieben Budget-Spalten wird ein {@link NightRunBudget} — oder {@code null}, wenn keine
   * davon gesetzt ist („nicht angegeben", Plan #1110 E3). Dieselbe Regel wie beim Verbrauch, und
   * aus demselben Grund.
   */
  private static @Nullable NightRunBudget budgetLesen(NightRunEntity e) {
    String origin = e.getBudgetOrigin();
    String defaultFields = e.getBudgetDefaultFields();
    if (e.getBudgetPlanMin() == null
        && e.getBudgetReviewMin() == null
        && e.getBudgetPaketeMin() == null
        && e.getBudgetAbdeckungMin() == null
        && e.getBudgetKostenUsd() == null
        && origin == null
        && defaultFields == null) {
      return null;
    }
    return new NightRunBudget(
        e.getBudgetPlanMin(),
        e.getBudgetReviewMin(),
        e.getBudgetPaketeMin(),
        e.getBudgetAbdeckungMin(),
        e.getBudgetKostenUsd(),
        origin == null ? null : NightRunBudgetOrigin.valueOf(origin),
        defaultFields == null ? List.of() : List.of(defaultFields.split(",")));
  }
}
