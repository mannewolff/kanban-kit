package org.mwolff.manban.nightrun.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.NightRunStuck;

/**
 * Die gemeldeten Angaben eines festgefahrenen Pakets (Issue #1549, Plan #1547 E8).
 *
 * <p>Eine eigene Datei statt eines verschachtelten Records im Ingest-Controller, aus demselben
 * Grund wie {@link NightRunUsageRequest}: Der Upload-Weg im {@code NightRunController} nimmt
 * denselben Typ an.
 *
 * <p><b>Jedes Feld darf fehlen</b> — eine fehlende Angabe heißt „nicht gemeldet". Die Grenzen sind
 * die Spaltenlängen aus {@code V50}: Ohne sie risse eine überlange Meldung dort in einen
 * Serverfehler statt in eine benannte Ablehnung. Ob die Angaben am Paket landen, entscheidet der
 * Dienst — nur bei {@code errorClass = STUCK} (E2).
 */
@Schema(
    description =
        """
        Angaben eines festgefahrenen Pakets (errorClass STUCK, state RED). Jedes Feld darf \
        fehlen: Was fehlt, gilt als nicht gemeldet. An einem Paket ohne STUCK wird der Block \
        angenommen, aber verworfen.""",
    example =
        """
        {"check":"mvn verify","error":"OpenApiIT: Vertrag weicht vom Schnappschuss ab",\
        "attempts":3,"sessionLimitMs":3600000,"sessionId":"a1b2c3d4"}""")
record IngestStuckRequest(
    @Schema(
            description = "Die Prüfung, an der das Paket hing; höchstens 300 Zeichen.",
            example = "mvn verify")
        @Nullable
        @Size(max = CHECK_MAX)
        String check,
    @Schema(
            description = "Der wiederkehrende Fehler; höchstens 1000 Zeichen.",
            example = "OpenApiIT: Vertrag weicht vom Schnappschuss ab")
        @Nullable
        @Size(max = ERROR_MAX)
        String error,
    @Schema(description = "Zahl der Versuche, mindestens 1.", example = "3") @Nullable @Min(1)
        Integer attempts,
    @Schema(
            description =
                """
                Zeitgrenze der Sitzung dieses Pakets in Millisekunden, mindestens 0. Mit der \
                Laufzeit (durationMs) ergibt sie die geschätzte gesparte Zeit.""",
            example = "3600000")
        @Nullable
        @Min(0)
        Long sessionLimitMs,
    @Schema(description = "Kennung der Sitzung; höchstens 100 Zeichen.", example = "a1b2c3d4")
        @Nullable
        @Size(max = SESSION_ID_MAX)
        String sessionId) {

  /** Längengrenze der Prüfung — die Spalte {@code stuck_check} aus {@code V50}. */
  static final int CHECK_MAX = 300;

  /** Längengrenze des Fehlers — die Spalte {@code stuck_error} aus {@code V50}. */
  static final int ERROR_MAX = 1000;

  /** Längengrenze der Sitzungskennung — die Spalte {@code stuck_session_id} aus {@code V50}. */
  static final int SESSION_ID_MAX = 100;

  private static final NightRunStuck KEINE_ANGABEN =
      new NightRunStuck(null, null, null, null, null);

  /**
   * {@code null}, wenn der Block fehlt oder leer ist — dann trägt das Paket keine Angaben. Ein
   * leerer Block wird wie ein fehlender behandelt, denn so liest ihn auch die Datenbank zurück
   * ({@code NightRunStuck}: ein Paket ohne jede Angabe trägt keinen Record).
   */
  static @Nullable NightRunStuck toDomain(@Nullable IngestStuckRequest request) {
    if (request == null) {
      return null;
    }
    NightRunStuck stuck =
        new NightRunStuck(
            request.check(),
            request.error(),
            request.attempts(),
            request.sessionLimitMs(),
            request.sessionId());
    return stuck.equals(KEINE_ANGABEN) ? null : stuck;
  }
}
