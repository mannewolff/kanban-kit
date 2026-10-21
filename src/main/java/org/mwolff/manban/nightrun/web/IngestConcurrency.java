package org.mwolff.manban.nightrun.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.NightRunConcurrency;

/**
 * Deutung der drei gemeldeten Angaben zur gleichzeitigen Umsetzung (Issue #1877, Plan #1876 E1–E4,
 * E17, E18) — für beide Einlieferungswege an einer Stelle.
 *
 * <p>Die Felder kommen als {@link JsonNode} herein statt als {@code Boolean}/{@code Long}: Jackson
 * wiese einen Text bei einem Wahrheitswert als unlesbaren Body ab, und eine Prüfregel wie
 * {@code @Min(0)} kostete den ganzen Lauf. Ein Wert außerhalb seines Wertebereichs wird hier zu
 * „nicht gemeldet“, die Meldung selbst wird angenommen.
 *
 * <p>Java-{@code null} heißt: Das Feld fehlt in der Meldung. Jeder andere Knoten, auch ein
 * ausdrückliches JSON-{@code null} ({@code NullNode}), gilt als geführt (E2).
 */
final class IngestConcurrency {

  /** Beschreibung von {@code concurrent} für die API-Seite. */
  static final String CONCURRENT_BESCHREIBUNG =
      """
      Gleichzeitige Umsetzung: true, wenn das Paket gleichzeitig mit mindestens einem anderen \
      Paket lief. Nur gemeldet, wenn die Nacht mit mehr als einem Platz lief. Ein Wert, der \
      kein Wahrheitswert ist (auch der Text "true"), gilt als nicht gemeldet; die Meldung wird \
      trotzdem angenommen.""";

  /** Beispiel für {@code concurrent}. */
  static final String CONCURRENT_BEISPIEL = "true";

  /** Beschreibung von {@code checkWaitMs} für die API-Seite. */
  static final String CHECK_WAIT_MS_BESCHREIBUNG =
      """
      Wartezeit auf einen freien Platz für den vollen Prüflauf in Millisekunden, mindestens 0; \
      darf fehlen. Die gemeldete Paketdauer `durationMs` enthält diese Wartezeit. Eine negative \
      Zahl, eine Gleitkommazahl oder ein Text gilt als nicht gemeldet; die Meldung wird \
      trotzdem angenommen.""";

  /** Beispiel für {@code checkWaitMs}. */
  static final String CHECK_WAIT_MS_BEISPIEL = "420000";

  /** Beschreibung von {@code deferredAtMerge} für die API-Seite. */
  static final String DEFERRED_AT_MERGE_BESCHREIBUNG =
      """
      Übernahme verschoben: true, wenn die Übernahme des Pakets in den gemeinsamen Stand \
      zurückgestellt wurde. Nur gemeldet, wenn die Nacht mit mehr als einem Platz lief. Ein \
      Wert, der kein Wahrheitswert ist, gilt als nicht gemeldet; die Meldung wird trotzdem \
      angenommen.""";

  /** Beispiel für {@code deferredAtMerge}. */
  static final String DEFERRED_AT_MERGE_BEISPIEL = "false";

  private IngestConcurrency() {}

  /**
   * Die drei gemeldeten Felder als Domänenwert.
   *
   * @return {@code null}, wenn alle drei Felder fehlen; sonst ein Record, dessen Komponente bei
   *     einem Wert außerhalb des Wertebereichs {@code null} ist
   */
  static @Nullable NightRunConcurrency toDomain(
      @Nullable JsonNode concurrent,
      @Nullable JsonNode checkWaitMs,
      @Nullable JsonNode deferredAtMerge) {
    if (concurrent == null && checkWaitMs == null && deferredAtMerge == null) {
      return null;
    }
    return new NightRunConcurrency(
        wahrheitswert(concurrent), wartezeit(checkWaitMs), wahrheitswert(deferredAtMerge));
  }

  private static @Nullable Boolean wahrheitswert(@Nullable JsonNode knoten) {
    return (knoten != null && knoten.isBoolean()) ? knoten.booleanValue() : null;
  }

  /** Nur eine JSON-Ganzzahl, die in ein {@code long} passt und nicht negativ ist (E3). */
  private static @Nullable Long wartezeit(@Nullable JsonNode knoten) {
    if (knoten == null || !knoten.isIntegralNumber() || !knoten.canConvertToLong()) {
      return null;
    }
    long wert = knoten.longValue();
    return wert < 0 ? null : wert;
  }
}
