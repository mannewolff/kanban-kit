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
 */
interface NightRunUsageJpaRepository extends Repository<NightRunEntity, Long> {

  /** Die aufbewahrten Läufe eines Projekts in der Spanne — Grundlage aller drei Summen. */
  String LAEUFE =
      "laeufe AS (SELECT r.id, r.started_at, r.duration_ms, r.cost_usd, r.input_tokens,"
          + " r.output_tokens, r.cached_input_tokens FROM night_run r"
          + " WHERE r.project_id = :projectId AND r.started_at >= :from AND r.started_at < :to)";

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
              + ", nacht AS (SELECT l.*,"
              + " ((l.started_at AT TIME ZONE :zone) - interval '12 hours')::date AS datum"
              + " FROM laeufe l),"
              + " lauf_summen AS (SELECT datum, count(*) AS runs,"
              + " sum(duration_ms)::bigint AS dauer,"
              + " sum(cost_usd) AS kosten, sum(input_tokens)::bigint AS eingabe,"
              + " sum(output_tokens)::bigint AS ausgabe,"
              + " sum(cached_input_tokens)::bigint AS zwischenspeicher FROM nacht GROUP BY datum),"
              + " paket_summen AS (SELECT n.datum, count(DISTINCT i.card_number) AS karten,"
              + " sum(i.cost_usd) AS kosten, sum(i.input_tokens)::bigint AS eingabe,"
              + " sum(i.output_tokens)::bigint AS ausgabe,"
              + " sum(i.cached_input_tokens)::bigint AS zwischenspeicher,"
              + " string_agg(DISTINCT i.error_class, ',') AS klassen"
              + " FROM night_run_item i JOIN nacht n ON n.id = i.night_run_id GROUP BY n.datum)"
              + " SELECT to_char(ls.datum, 'YYYY-MM-DD') AS \"night\", ls.runs AS \"runCount\","
              + " ls.dauer AS \"durationMs\", coalesce(ps.karten, 0) AS \"cardCount\","
              + " ls.kosten AS \"runCostUsd\", ls.eingabe AS \"runInputTokens\","
              + " ls.ausgabe AS \"runOutputTokens\","
              + " ls.zwischenspeicher AS \"runCachedInputTokens\","
              + " ps.kosten AS \"itemCostUsd\", ps.eingabe AS \"itemInputTokens\","
              + " ps.ausgabe AS \"itemOutputTokens\","
              + " ps.zwischenspeicher AS \"itemCachedInputTokens\", ps.klassen AS \"errorClasses\""
              + " FROM lauf_summen ls LEFT JOIN paket_summen ps ON ps.datum = ls.datum"
              + " ORDER BY ls.datum",
      nativeQuery = true)
  List<NightRow> totalsPerNight(
      @Param("projectId") long projectId,
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("zone") String zone);

  /** Je Kartennummer über alle Anläufe der Spanne. */
  @Query(
      value =
          "WITH "
              + LAEUFE
              + " SELECT i.card_number AS \"cardNumber\", count(*) AS \"attemptCount\","
              + " sum(i.duration_ms)::bigint AS \"durationMs\", sum(i.cost_usd) AS \"costUsd\","
              + " sum(i.input_tokens)::bigint AS \"inputTokens\","
              + " sum(i.output_tokens)::bigint AS \"outputTokens\","
              + " sum(i.cached_input_tokens)::bigint AS \"cachedInputTokens\""
              + " FROM night_run_item i JOIN laeufe l ON l.id = i.night_run_id"
              + " GROUP BY i.card_number ORDER BY i.card_number",
      nativeQuery = true)
  List<CardRow> totalsPerCard(
      @Param("projectId") long projectId, @Param("from") Instant from, @Param("to") Instant to);

  /** Die Gesamtsummen; Aggregate ohne {@code GROUP BY} liefern stets genau eine Zeile. */
  @Query(
      value =
          "WITH "
              + LAEUFE
              + ", l AS (SELECT count(*) AS runs, coalesce(sum(duration_ms), 0)::bigint AS dauer,"
              + " sum(cost_usd) AS kosten, sum(input_tokens)::bigint AS eingabe,"
              + " sum(output_tokens)::bigint AS ausgabe,"
              + " sum(cached_input_tokens)::bigint AS zwischenspeicher FROM laeufe),"
              + " p AS (SELECT count(DISTINCT i.card_number) AS karten, sum(i.cost_usd) AS kosten,"
              + " sum(i.input_tokens)::bigint AS eingabe, sum(i.output_tokens)::bigint AS ausgabe,"
              + " sum(i.cached_input_tokens)::bigint AS zwischenspeicher"
              + " FROM night_run_item i JOIN laeufe x ON x.id = i.night_run_id)"
              + " SELECT l.runs AS \"runCount\", l.dauer AS \"durationMs\","
              + " p.karten AS \"cardCount\", l.kosten AS \"runCostUsd\","
              + " l.eingabe AS \"runInputTokens\", l.ausgabe AS \"runOutputTokens\","
              + " l.zwischenspeicher AS \"runCachedInputTokens\", p.kosten AS \"itemCostUsd\","
              + " p.eingabe AS \"itemInputTokens\", p.ausgabe AS \"itemOutputTokens\","
              + " p.zwischenspeicher AS \"itemCachedInputTokens\" FROM l CROSS JOIN p",
      nativeQuery = true)
  TotalsRow totals(
      @Param("projectId") long projectId, @Param("from") Instant from, @Param("to") Instant to);

  /** JPQL statt nativ: Der Typ des Startzeitpunkts kommt so als {@link Instant} aus der Entity. */
  @Query("select min(r.startedAt) from NightRunEntity r where r.projectId = :projectId")
  Optional<Instant> oldestStartedAt(@Param("projectId") long projectId);

  /** Die vier Verbräuche eines Laufs und seiner Pakete, wie beide Summen-Zeilen sie tragen. */
  interface UsageColumns {
    @Nullable BigDecimal getRunCostUsd();

    @Nullable Long getRunInputTokens();

    @Nullable Long getRunOutputTokens();

    @Nullable Long getRunCachedInputTokens();

    @Nullable BigDecimal getItemCostUsd();

    @Nullable Long getItemInputTokens();

    @Nullable Long getItemOutputTokens();

    @Nullable Long getItemCachedInputTokens();
  }

  /** Eine Nacht. */
  interface NightRow extends UsageColumns {
    String getNight();

    long getRunCount();

    long getDurationMs();

    long getCardCount();

    @Nullable String getErrorClasses();
  }

  /** Eine Kartennummer. */
  interface CardRow {
    int getCardNumber();

    long getAttemptCount();

    @Nullable Long getDurationMs();

    @Nullable BigDecimal getCostUsd();

    @Nullable Long getInputTokens();

    @Nullable Long getOutputTokens();

    @Nullable Long getCachedInputTokens();
  }

  /** Die Gesamtsummen. */
  interface TotalsRow extends UsageColumns {
    long getRunCount();

    long getDurationMs();

    long getCardCount();
  }
}
