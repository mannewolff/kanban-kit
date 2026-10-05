package org.mwolff.manban.nightrun.web;

import io.swagger.v3.oas.annotations.media.Schema;
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
 *
 * <p>Seit Issue #1113 nimmt der Typ auch Modellzeit und Züge an (Plan #1110 E1). Beide sind
 * {@code @Nullable} und ohne {@code @NotNull}, wie jede Erweiterung dieses Vertrags: Eine ältere
 * Kit-Kopie kennt sie nicht und meldet unverändert weiter. Der Browser-Upload nimmt sie über
 * denselben Typ formal an, sendet sie aber nicht (E14).
 *
 * @param modelDurationMs die Zeit, die das Modell gerechnet hat — nicht die Wanduhr-Dauer
 * @param turns Zahl der Züge der Sitzung
 */
@Schema(
    description =
        "Gemeldeter Verbrauch eines Laufs, eines Vorgangs oder einer Stufe. Jedes Feld darf"
            + " fehlen: Was fehlt, gilt als nicht gemessen, nicht als 0.")
record NightRunUsageRequest(
    @Schema(description = "Kosten in US-Dollar.", example = "1.25") @Nullable BigDecimal costUsd,
    @Schema(description = "Eingabe-Tokens.", example = "120000") @Nullable Long inputTokens,
    @Schema(description = "Ausgabe-Tokens.", example = "8000") @Nullable Long outputTokens,
    @Schema(description = "Davon aus dem Cache gelesene Eingabe-Tokens.", example = "90000")
        @Nullable Long cachedInputTokens,
    @Schema(
            description = "Rechenzeit des Modells in Millisekunden, nicht die Wanduhr-Dauer.",
            example = "420000")
        @Nullable Long modelDurationMs,
    @Schema(description = "Zahl der Züge der Sitzung.", example = "57") @Nullable Integer turns) {

  /** {@code null}, wenn gar kein Verbrauch gemeldet wurde — dann steht am Lauf „nicht gemessen". */
  static @Nullable NightRunUsage toDomain(@Nullable NightRunUsageRequest request) {
    return request == null
        ? null
        : new NightRunUsage(
            request.costUsd(),
            request.inputTokens(),
            request.outputTokens(),
            request.cachedInputTokens(),
            request.modelDurationMs(),
            request.turns());
  }
}
