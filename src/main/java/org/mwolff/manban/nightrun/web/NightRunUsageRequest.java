package org.mwolff.manban.nightrun.web;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.NightRunUsage;

/**
 * Die gemeldeten Verbrauchszahlen eines Laufs oder eines Arbeitspakets (Issue #947).
 *
 * <p>Eine eigene Datei statt eines verschachtelten Records im Ingest-Controller: Der bestehende
 * {@code NightRunController} braucht denselben Typ, sobald er den Kostenwert annimmt — ein
 * verschachtelter Typ eines fremden Controllers wäre dort ein schiefer Import.
 *
 * <p>Jedes Feld darf fehlen. Wer nichts meldet, hat nichts gemessen; eine 0 behauptete, es sei
 * nichts verbraucht worden.
 */
record NightRunUsageRequest(
    @Nullable BigDecimal costUsd,
    @Nullable Long inputTokens,
    @Nullable Long outputTokens,
    @Nullable Long cachedInputTokens) {

  /** {@code null}, wenn gar kein Verbrauch gemeldet wurde — dann steht am Lauf „nicht gemessen". */
  static @Nullable NightRunUsage toDomain(@Nullable NightRunUsageRequest request) {
    return request == null
        ? null
        : new NightRunUsage(
            request.costUsd(),
            request.inputTokens(),
            request.outputTokens(),
            request.cachedInputTokens());
  }
}
