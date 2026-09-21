package org.mwolff.manban.nightrun.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Aggregat-Abfragen der Verbrauchs-Auswertung über {@code night_run} und {@code night_run_item}
 * (Issue #937, Plan #933).
 *
 * <p>Native Abfragen, <b>alle Parameter gebunden</b> — auch die Zonen-ID, als Text und nie als
 * Textbaustein der Abfrage (Plan E4). Postgres nimmt einen IANA-Namen in {@code AT TIME ZONE}
 * entgegen; Offset-Zonen weist die Web-Schicht vorher ab, weil Postgres POSIX-Offsets mit
 * umgekehrtem Vorzeichen deutet.
 *
 * <p>Die Aliase stehen in Anführungszeichen: Postgres schriebe sie sonst klein, und die Projektion
 * fände ihre Getter nicht.
 *
 * <p>{@code sum} über lauter {@code NULL} ist {@code NULL} — genau das trägt „nicht gemessen" bis
 * in die Domäne (Plan E5). Die Mengen werden auf {@code bigint} zurückgeführt, weil {@code sum}
 * über {@code bigint} in Postgres {@code numeric} liefert.
 *
 * <p><b>Getrennt nach Gattung</b> (Issue #1013): Jede Summe kommt zweimal, gefiltert über {@code
 * kind}. {@code FILTER} statt {@code GROUP BY kind}, weil eine Gattung ohne Eintrag sonst als Zeile
 * fehlte statt als fehlender Wert zu erscheinen — und „nicht gemessen" soll fehlend bleiben und
 * nicht zu 0 werden. Die Gattungsnamen stehen als Literale und nicht als Parameter: Sie sind
 * Konstanten der Domäne ({@code NightRunKind}), keine Eingabe von außen.
 *
 * <p>Die Arbeitspakete werden nach der Gattung <b>ihres Laufs</b> gruppiert und nicht nach ihrer
 * eigenen Spalte. Beide tragen denselben Wert — der Adapter schreibt die Gattung des Laufs an das
 * Paket —, aber nur so gehört jedes Paket zu genau der Gattung, deren Lauf-Summe der nicht
 * zuordenbare Rest davon abzieht (Plan E6, #984 AK 5).
 */
interface NightRunUsageJpaRepository extends Repository<NightRunEntity, Long> {

  /** Die aufbewahrten Läufe eines Projekts, noch ohne Zeitgrenze — offen für deren Anhang. */
  String LAUF_QUELLE =
      "laeufe AS (SELECT r.id, r.started_at, r.duration_ms, r.kind, r.cost_usd, r.input_tokens,"
          + " r.output_tokens, r.cached_input_tokens FROM night_run r"
          + " WHERE r.project_id = :projectId";

  /** Die aufbewahrten Läufe eines Projekts in der Spanne — Grundlage aller drei Summen. */
  String LAEUFE = LAUF_QUELLE + " AND r.started_at >= :from AND r.started_at < :to)";

  /**
   * Alle aufbewahrten Läufe und Sitzungen eines Projekts über seine ganze Laufzeit (Issue #1014) —
   * dieselben Spalten wie {@link #LAEUFE}, nur ohne die Zeitgrenzen. Geteilt statt zweimal
   * geschrieben: Zwei Spaltenlisten liefen sonst unbemerkt auseinander.
   */
  String ALLE_LAEUFE = LAUF_QUELLE + ")";

  /** Zahl und Verbrauch der Einträge je Gattung; erwartet die Spalten der Lauf-Menge. */
  String LAUF_JE_GATTUNG =
      " count(*) FILTER (WHERE kind = 'NIGHT') AS runs_n,"
          + " count(*) FILTER (WHERE kind = 'INTERACTIVE') AS runs_i,"
          + " coalesce(sum(duration_ms), 0)::bigint AS dauer,"
          + " sum(cost_usd) FILTER (WHERE kind = 'NIGHT') AS kosten_n,"
          + " (sum(input_tokens) FILTER (WHERE kind = 'NIGHT'))::bigint AS eingabe_n,"
          + " (sum(output_tokens) FILTER (WHERE kind = 'NIGHT'))::bigint AS ausgabe_n,"
          + " (sum(cached_input_tokens) FILTER (WHERE kind = 'NIGHT'))::bigint AS zwischen_n,"
          + " sum(cost_usd) FILTER (WHERE kind = 'INTERACTIVE') AS kosten_i,"
          + " (sum(input_tokens) FILTER (WHERE kind = 'INTERACTIVE'))::bigint AS eingabe_i,"
          + " (sum(output_tokens) FILTER (WHERE kind = 'INTERACTIVE'))::bigint AS ausgabe_i,"
          + " (sum(cached_input_tokens) FILTER (WHERE kind = 'INTERACTIVE'))::bigint AS zwischen_i";

  /** Verbrauch der Arbeitspakete je Gattung; {@code x} ist der Lauf, {@code i} das Paket. */
  String PAKET_JE_GATTUNG =
      " count(DISTINCT i.card_number) AS karten,"
          + " sum(i.cost_usd) FILTER (WHERE x.kind = 'NIGHT') AS kosten_n,"
          + " (sum(i.input_tokens) FILTER (WHERE x.kind = 'NIGHT'))::bigint AS eingabe_n,"
          + " (sum(i.output_tokens) FILTER (WHERE x.kind = 'NIGHT'))::bigint AS ausgabe_n,"
          + " (sum(i.cached_input_tokens) FILTER (WHERE x.kind = 'NIGHT'))::bigint AS zwischen_n,"
          + " sum(i.cost_usd) FILTER (WHERE x.kind = 'INTERACTIVE') AS kosten_i,"
          + " (sum(i.input_tokens) FILTER (WHERE x.kind = 'INTERACTIVE'))::bigint AS eingabe_i,"
          + " (sum(i.output_tokens) FILTER (WHERE x.kind = 'INTERACTIVE'))::bigint AS ausgabe_i,"
          + " (sum(i.cached_input_tokens) FILTER (WHERE x.kind = 'INTERACTIVE'))::bigint"
          + " AS zwischen_i";

  /**
   * Die Spalten beider Summen-Zeilen unter den Namen der Projektion; {@code l} Lauf, {@code p}
   * Paket.
   */
  String SPALTEN =
      " l.runs_n AS \"nightRunCount\", l.runs_i AS \"interactiveRunCount\","
          + " l.dauer AS \"durationMs\", coalesce(p.karten, 0) AS \"cardCount\","
          + " l.kosten_n AS \"nightRunCostUsd\", l.eingabe_n AS \"nightRunInputTokens\","
          + " l.ausgabe_n AS \"nightRunOutputTokens\","
          + " l.zwischen_n AS \"nightRunCachedInputTokens\","
          + " l.kosten_i AS \"interactiveRunCostUsd\","
          + " l.eingabe_i AS \"interactiveRunInputTokens\","
          + " l.ausgabe_i AS \"interactiveRunOutputTokens\","
          + " l.zwischen_i AS \"interactiveRunCachedInputTokens\","
          + " p.kosten_n AS \"nightItemCostUsd\", p.eingabe_n AS \"nightItemInputTokens\","
          + " p.ausgabe_n AS \"nightItemOutputTokens\","
          + " p.zwischen_n AS \"nightItemCachedInputTokens\","
          + " p.kosten_i AS \"interactiveItemCostUsd\","
          + " p.eingabe_i AS \"interactiveItemInputTokens\","
          + " p.ausgabe_i AS \"interactiveItemOutputTokens\","
          + " p.zwischen_i AS \"interactiveItemCachedInputTokens\"";

  /**
   * Je Nacht. Das Datum der Nacht ist das zonenlokale Datum 12 Stunden vor dem Start: Ein Lauf vor
   * 12:00 zählt zur vorangegangenen Nacht (Issue #969). Als Text geliefert, weil ein {@code date}
   * aus einer nativen Abfrage je nach Treiber als {@code java.sql.Date} oder {@code LocalDate}
   * ankommt.
   */
  @Query(
      value =
          "WITH "
              + LAEUFE
              + ", nacht AS (SELECT q.*,"
              + " ((q.started_at AT TIME ZONE :zone) - interval '12 hours')::date AS datum"
              + " FROM laeufe q),"
              + " l AS (SELECT datum,"
              + LAUF_JE_GATTUNG
              + " FROM nacht GROUP BY datum),"
              + " p AS (SELECT x.datum,"
              + PAKET_JE_GATTUNG
              + ", string_agg(DISTINCT i.error_class, ',') AS klassen"
              + " FROM night_run_item i JOIN nacht x ON x.id = i.night_run_id GROUP BY x.datum)"
              + " SELECT to_char(l.datum, 'YYYY-MM-DD') AS \"night\","
              + SPALTEN
              + ", p.klassen AS \"errorClasses\""
              + " FROM l LEFT JOIN p ON p.datum = l.datum"
              + " ORDER BY l.datum",
      nativeQuery = true)
  List<NightRow> totalsPerNight(
      @Param("projectId") long projectId,
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("zone") String zone);

  /** Je Kartennummer über alle Anläufe der Spanne, der Verbrauch je Gattung getrennt. */
  @Query(
      value =
          "WITH "
              + LAEUFE
              + " SELECT i.card_number AS \"cardNumber\", count(*) AS \"attemptCount\","
              + " sum(i.duration_ms)::bigint AS \"durationMs\","
              + " sum(i.cost_usd) FILTER (WHERE l.kind = 'NIGHT') AS \"nightCostUsd\","
              + " (sum(i.input_tokens) FILTER (WHERE l.kind = 'NIGHT'))::bigint"
              + " AS \"nightInputTokens\","
              + " (sum(i.output_tokens) FILTER (WHERE l.kind = 'NIGHT'))::bigint"
              + " AS \"nightOutputTokens\","
              + " (sum(i.cached_input_tokens) FILTER (WHERE l.kind = 'NIGHT'))::bigint"
              + " AS \"nightCachedInputTokens\","
              + " sum(i.cost_usd) FILTER (WHERE l.kind = 'INTERACTIVE') AS \"interactiveCostUsd\","
              + " (sum(i.input_tokens) FILTER (WHERE l.kind = 'INTERACTIVE'))::bigint"
              + " AS \"interactiveInputTokens\","
              + " (sum(i.output_tokens) FILTER (WHERE l.kind = 'INTERACTIVE'))::bigint"
              + " AS \"interactiveOutputTokens\","
              + " (sum(i.cached_input_tokens) FILTER (WHERE l.kind = 'INTERACTIVE'))::bigint"
              + " AS \"interactiveCachedInputTokens\""
              + " FROM night_run_item i JOIN laeufe l ON l.id = i.night_run_id"
              + " GROUP BY i.card_number ORDER BY i.card_number",
      nativeQuery = true)
  List<CardRow> totalsPerCard(
      @Param("projectId") long projectId, @Param("from") Instant from, @Param("to") Instant to);

  /**
   * Die Summen einer Stufe; {@code s} ist die Stufenzeile. Eigene Spalten statt {@link
   * #PAKET_JE_GATTUNG}: Eine Stufe der Kette gibt es nur im Nachtlauf, und eine nach Gattung
   * geteilte Summe trüge hier auf Dauer eine Spalte, die immer leer bliebe.
   */
  String STUFE_JE_ZEILE =
      " s.stage AS \"stage\", count(*) AS \"itemCount\","
          + " sum(s.duration_ms)::bigint AS \"durationMs\","
          + " sum(s.cost_usd) AS \"costUsd\","
          + " (sum(s.input_tokens))::bigint AS \"inputTokens\","
          + " (sum(s.output_tokens))::bigint AS \"outputTokens\","
          + " (sum(s.cached_input_tokens))::bigint AS \"cachedInputTokens\","
          + " (sum(s.model_duration_ms))::bigint AS \"modelDurationMs\","
          + " (sum(s.turns))::int AS \"turns\"";

  /**
   * Je Stufe der Kette über alle Vorgänge der Spanne (Issue #1114). Der {@code JOIN} über {@code
   * night_run_item} auf {@link #LAEUFE} bindet die Stufen an Projekt und Spanne und lässt zugleich
   * Läufe ohne Stufen draußen — daher gibt es hier keine Zeile „ohne Stufe" (Plan E6).
   *
   * <p>Die Reihenfolge der Kette stellt der Adapter her: {@code ORDER BY s.stage} sortierte
   * alphabetisch, und ein {@code CASE} in der Abfrage schriebe die Reihenfolge von {@code
   * NightRunStage} ein zweites Mal auf.
   */
  @Query(
      value =
          "WITH "
              + LAEUFE
              + " SELECT"
              + STUFE_JE_ZEILE
              + " FROM night_run_item_stage s"
              + " JOIN night_run_item i ON i.id = s.night_run_item_id"
              + " JOIN laeufe l ON l.id = i.night_run_id"
              + " GROUP BY s.stage",
      nativeQuery = true)
  List<StageRow> totalsPerStage(
      @Param("projectId") long projectId, @Param("from") Instant from, @Param("to") Instant to);

  /** Die Gesamtsummen; Aggregate ohne {@code GROUP BY} liefern stets genau eine Zeile. */
  @Query(
      value =
          "WITH "
              + LAEUFE
              + ", l AS (SELECT"
              + LAUF_JE_GATTUNG
              + " FROM laeufe),"
              + " p AS (SELECT"
              + PAKET_JE_GATTUNG
              + " FROM night_run_item i JOIN laeufe x ON x.id = i.night_run_id)"
              + " SELECT"
              + SPALTEN
              + " FROM l CROSS JOIN p",
      nativeQuery = true)
  TotalsRow totals(
      @Param("projectId") long projectId, @Param("from") Instant from, @Param("to") Instant to);

  /**
   * Die Summen über die ganze Laufzeit — dieselbe Rechnung wie {@link #totals}, nur ohne die
   * Zeitgrenzen (Issue #1014). Die Laufdauer kommt über {@link #SPALTEN} mit und bleibt ungelesen:
   * Sie geteilt zu lassen ist billiger, als eine zweite Spaltenliste zu pflegen.
   */
  @Query(
      value =
          "WITH "
              + ALLE_LAEUFE
              + ", l AS (SELECT"
              + LAUF_JE_GATTUNG
              + " FROM laeufe),"
              + " p AS (SELECT"
              + PAKET_JE_GATTUNG
              + " FROM night_run_item i JOIN laeufe x ON x.id = i.night_run_id)"
              + " SELECT"
              + SPALTEN
              + " FROM l CROSS JOIN p",
      nativeQuery = true)
  TotalsRow lifetimeTotals(@Param("projectId") long projectId);

  /** JPQL statt nativ: Der Typ des Startzeitpunkts kommt so als {@link Instant} aus der Entity. */
  @Query("select min(r.startedAt) from NightRunEntity r where r.projectId = :projectId")
  Optional<Instant> oldestStartedAt(@Param("projectId") long projectId);

  /**
   * Zahl und ältester Startzeitpunkt je Gattung (Issue #1071, Plan #1067, E8) — Grundlage der
   * Aufbewahrungsgrenze. JPQL statt nativ, aus demselben Grund wie {@link #oldestStartedAt}: Der
   * Startzeitpunkt kommt so als {@link Instant} aus der Entity. Eine Gattung ohne Eintrag liefert
   * keine Zeile — das gleicht der Adapter aus.
   */
  @Query(
      "select r.kind as kind, count(r) as count, min(r.startedAt) as oldestStart"
          + " from NightRunEntity r where r.projectId = :projectId group by r.kind")
  List<RetentionRow> retentionByKind(@Param("projectId") long projectId);

  /**
   * Die Verbräuche von Läufen und Paketen je Gattung, wie beide Summen-Zeilen sie tragen. Die
   * Zählung kommt mit, weil eine Nacht ohne Lauf, aber mit Sitzungen, sonst nicht als besetzt
   * erkennbar wäre (Issue #1013).
   */
  interface UsageColumns {
    long getNightRunCount();

    long getInteractiveRunCount();

    @Nullable BigDecimal getNightRunCostUsd();

    @Nullable Long getNightRunInputTokens();

    @Nullable Long getNightRunOutputTokens();

    @Nullable Long getNightRunCachedInputTokens();

    @Nullable BigDecimal getInteractiveRunCostUsd();

    @Nullable Long getInteractiveRunInputTokens();

    @Nullable Long getInteractiveRunOutputTokens();

    @Nullable Long getInteractiveRunCachedInputTokens();

    @Nullable BigDecimal getNightItemCostUsd();

    @Nullable Long getNightItemInputTokens();

    @Nullable Long getNightItemOutputTokens();

    @Nullable Long getNightItemCachedInputTokens();

    @Nullable BigDecimal getInteractiveItemCostUsd();

    @Nullable Long getInteractiveItemInputTokens();

    @Nullable Long getInteractiveItemOutputTokens();

    @Nullable Long getInteractiveItemCachedInputTokens();
  }

  /** Eine Nacht. */
  interface NightRow extends UsageColumns {
    String getNight();

    long getDurationMs();

    long getCardCount();

    @Nullable String getErrorClasses();
  }

  /** Eine Kartennummer. */
  interface CardRow {
    int getCardNumber();

    long getAttemptCount();

    @Nullable Long getDurationMs();

    @Nullable BigDecimal getNightCostUsd();

    @Nullable Long getNightInputTokens();

    @Nullable Long getNightOutputTokens();

    @Nullable Long getNightCachedInputTokens();

    @Nullable BigDecimal getInteractiveCostUsd();

    @Nullable Long getInteractiveInputTokens();

    @Nullable Long getInteractiveOutputTokens();

    @Nullable Long getInteractiveCachedInputTokens();
  }

  /** Eine Stufe der Kette; {@code stage} kommt als Enum-Name aus der Spalte. */
  interface StageRow {
    String getStage();

    long getItemCount();

    @Nullable Long getDurationMs();

    @Nullable BigDecimal getCostUsd();

    @Nullable Long getInputTokens();

    @Nullable Long getOutputTokens();

    @Nullable Long getCachedInputTokens();

    @Nullable Long getModelDurationMs();

    @Nullable Integer getTurns();
  }

  /** Die Gesamtsummen. */
  interface TotalsRow extends UsageColumns {
    long getDurationMs();

    long getCardCount();
  }

  /** Eine Zeile von {@link #retentionByKind}; {@code kind} kommt als Enum-Name der Entity. */
  interface RetentionRow {
    String getKind();

    long getCount();

    @Nullable Instant getOldestStart();
  }
}
