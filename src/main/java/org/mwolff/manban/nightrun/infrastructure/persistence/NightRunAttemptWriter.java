package org.mwolff.manban.nightrun.infrastructure.persistence;

import java.sql.Types;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.NightRunAttempt;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

/**
 * Schreibt die Vorgänge eines Arbeitspakets (Issue #1944, Plan #1905 E4) — der Schreibweg der
 * Tabelle {@code night_run_attempt}, gerufen vom {@link NightRunRepositoryAdapter} nach jedem
 * Paket.
 *
 * <p>Eine eigene Klasse, weil der Adapter laut seiner eigenen Begründung an der Grenze steht: Wenn
 * dort weiter wächst, ist die Tabelle zu zerlegen — und eine neue Tabelle bringt hier ihr eigenes
 * Mapping mit, statt die PMD-Schwellen des Adapters ein weiteres Mal zu verschieben.
 *
 * <p>Ersetzt wird hier nichts: Beim Ersetzen eines Laufs fallen die Vorgänge mit ihren Paketen über
 * {@code ON DELETE CASCADE} und werden mit den neuen Paketen neu geschrieben (Plan #1905 E5).
 */
@Component
class NightRunAttemptWriter {

  /**
   * Die Kartenliste geht als {@code Integer[]} mit {@code Types.ARRAY} in die erste Array-Spalte
   * des Schemas; pgjdbc bindet sie als {@code int4[]}.
   */
  private static final String INSERT_ATTEMPT =
      "INSERT INTO night_run_attempt (night_run_item_id, position, stage, card_number, model,"
          + " effort, succeeded, cost_usd, input_tokens, output_tokens, cached_input_tokens,"
          + " model_duration_ms, turns, written_card_numbers, run_complete)"
          + " VALUES (:nightRunItemId, :position, :stage, :cardNumber, :model, :effort,"
          + " :succeeded, :costUsd, :inputTokens, :outputTokens, :cachedInputTokens,"
          + " :modelDurationMs, :turns, :writtenCardNumbers, :runComplete)";

  private final NamedParameterJdbcTemplate jdbc;

  NightRunAttemptWriter(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Die Vorgänge eines Pakets als Stapel, {@code position} in Meldungsreihenfolge — oder nichts.
   *
   * @param itemId das frisch geschriebene Paket
   * @param attempts die Vorgänge in der Reihenfolge ihrer Ausführung
   * @param runComplete der Wert {@code complete} des gemeldeten Laufs; weil jede Meldung des Kits
   *     die Vorgänge ersetzt, trägt die Spalte den Wert der letzten Meldung
   */
  void insert(@Nullable Long itemId, List<NightRunAttempt> attempts, boolean runComplete) {
    if (attempts.isEmpty()) {
      return;
    }
    SqlParameterSource[] batch = new SqlParameterSource[attempts.size()];
    for (int position = 0; position < attempts.size(); position++) {
      NightRunAttempt vorgang = attempts.get(position);
      MapSqlParameterSource parameter =
          new MapSqlParameterSource()
              .addValue("nightRunItemId", itemId)
              .addValue("position", position)
              .addValue("stage", vorgang.stage().name())
              .addValue("cardNumber", vorgang.cardNumber())
              .addValue("model", vorgang.model(), Types.VARCHAR)
              .addValue("effort", vorgang.effort(), Types.VARCHAR)
              .addValue("succeeded", vorgang.succeeded())
              .addValue(
                  "writtenCardNumbers", vorgang.writtenCards().toArray(Integer[]::new), Types.ARRAY)
              .addValue("runComplete", runComplete);
      NightRunRepositoryAdapter.verbrauchSchreiben(parameter, vorgang.usage());
      batch[position] = parameter;
    }
    jdbc.batchUpdate(INSERT_ATTEMPT, batch);
  }
}
