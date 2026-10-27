package org.mwolff.manban.nightrun.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.NightRunAttempt;
import org.mwolff.manban.nightrun.domain.NightRunAttemptStage;

/**
 * Ein gemeldeter Vorgang eines Arbeitspakets nach Vertrag V1 (Issue #1945, Plan #1905 E1, E3, E6).
 *
 * <p>Eine eigene Datei wie {@link NightRunUsageRequest}: Beide Einlieferungswege — die Meldung des
 * Kits ({@code NightRunIngestController}) und die von Hand eingelesene Ergebnisdatei ({@code
 * NightRunController}) — nehmen denselben Typ an (E16).
 *
 * <p>Pflicht sind {@code stage}, {@code cardNumber} und {@code succeeded}: Sie machen den Eintrag
 * aus. Alles andere darf fehlen (E6). Eine unbekannte Stufe weist die Meldung ab — {@link
 * NightRunAttemptStage#vonName} wirft, und der Body ist dann nicht lesbar (400), dieselbe Regel wie
 * bei den Stufen eines Pakets ({@link NightRunIngestController.IngestStageRequest}). Die Längen-
 * und Anzahlgrenzen folgen den Spalten von {@code night_run_attempt}: Ohne sie risse eine überlange
 * Meldung dort in einen Serverfehler statt in eine benannte Ablehnung.
 *
 * @param stage die Stufe, in der der Vorgang lief
 * @param cardNumber die Karte, an der der Vorgang arbeitete
 * @param model das Modell, wie der Runner es führt; fehlt es, zählt der Vorgang im Vergleich nicht
 * @param effort die Denktiefe, wie gemeldet
 * @param succeeded ob der Vorgang gelungen ist
 * @param usage Verbrauch des Vorgangs
 * @param writtenCards die Karten der Dokumente, die der Vorgang geschrieben hat
 */
@Schema(
    description =
        """
        Ein einzelner Vorgang des Pakets: ein Versuch an einer Karte in einer Stufe, in der \
        Prüfung je Prüfer ein eigener Eintrag.""")
record NightRunAttemptRequest(
    @Schema(
            description =
                """
                Die Stufe: PLAN, REVIEW, PAKETE, ABDECKUNG oder UMSETZUNG; angenommen werden \
                auch PACKAGES, COVERAGE und IMPLEMENTATION, in beliebiger Schreibweise.""",
            example = "UMSETZUNG")
        @NotNull
        NightRunAttemptStage stage,
    @Schema(
            description = "Projektweite Nummer der Karte, an der der Vorgang arbeitete.",
            example = "1945")
        @NotNull
        Integer cardNumber,
    @Schema(
            description = "Modell, wie der Runner es führt; höchstens 100 Zeichen.",
            example = "claude-opus-5-5")
        @Nullable
        @Size(max = MODEL_MAX)
        String model,
    @Schema(description = "Denktiefe, wie gemeldet; höchstens 20 Zeichen.", example = "high")
        @Nullable
        @Size(max = EFFORT_MAX)
        String effort,
    @Schema(description = "true, wenn der Vorgang gelungen ist.", example = "true") @NotNull
        Boolean succeeded,
    @Schema(description = "Verbrauch dieses Vorgangs.") @Nullable @Valid NightRunUsageRequest usage,
    @Schema(
            description =
                """
                Karten der Dokumente, die der Vorgang angelegt oder neu geschrieben hat; \
                höchstens 50.""",
            example = "[1905]")
        @Nullable
        @Size(max = WRITTEN_CARDS_MAX)
        List<@NotNull Integer> writtenCards) {

  /** Beschreibung des Felds {@code attempts} am Paket, gleich an beiden Einlieferungswegen. */
  static final String LISTE_BESCHREIBUNG =
      """
      Die einzelnen Vorgänge des Pakets in der Reihenfolge ihrer Ausführung, höchstens 50. \
      Fehlt das Feld, trägt das Paket keinen gemeldeten Vorgang.""";

  /** Obergrenze des Modellnamens — die Länge der Spalte {@code model}. */
  static final int MODEL_MAX = 100;

  /** Obergrenze der Denktiefe — die Länge der Spalte {@code effort}. */
  static final int EFFORT_MAX = 20;

  /** Obergrenze der geschriebenen Karten je Vorgang (E6). */
  static final int WRITTEN_CARDS_MAX = 50;

  /** Obergrenze der Vorgänge je Paket (E6). */
  static final int MAX_ATTEMPTS_PER_ITEM = 50;

  /** Die Vorgänge eines Pakets als Domänenwerte — fehlt die Liste, ist sie leer (E6). */
  static List<NightRunAttempt> toDomain(@Nullable List<NightRunAttemptRequest> requests) {
    return requests == null
        ? List.of()
        : requests.stream().map(NightRunAttemptRequest::toDomain).toList();
  }

  private NightRunAttempt toDomain() {
    return new NightRunAttempt(
        stage,
        cardNumber,
        model,
        effort,
        succeeded,
        NightRunUsageRequest.toDomain(usage),
        writtenCards == null ? List.of() : writtenCards);
  }
}
