package org.mwolff.manban.nightrun.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.RunnerStufe;

/**
 * Eine gemeldete laufende Stufe (Issue #1725, Plan #1724 Vertrag V1).
 *
 * <p>{@code stage} und {@code step} sind Zeichenketten ohne Muster und ohne Enum (Plan #1724 E5):
 * Ein neueres Kit mit einem weiteren Code verlöre sonst den ganzen Laufbericht, entgegen der Regel
 * dieses Endpunkts, nur hinzuzufügen. Begrenzt ist allein die Länge — die Spalten aus {@code V53}.
 */
@Schema(
    description =
        """
        Eine Stufe, die der Lauf gerade fährt. stage und step sind Codes; ein unbekannter \
        Code wird angenommen und wörtlich angezeigt.""",
    example =
        """
        {"cardNumber":1665,"stage":"VORBEREITUNG","startedAt":"2026-10-13T22:41:07Z",\
        "deadline":"2026-10-14T00:41:07Z","step":"WAITING_FOR_IMPLEMENTATION",\
        "holdsImplementation":false}""")
record IngestCurrentStageRequest(
    @Schema(description = "Projektweite Nummer der Karte der Stufe.", example = "1665")
        int cardNumber,
    @Schema(
            description =
                """
                Die Stufe, höchstens 40 Zeichen. Heute PLAN, REVIEW, PAKETE, ABDECKUNG, \
                UMSETZUNG oder VORBEREITUNG.""",
            example = "VORBEREITUNG")
        @NotBlank
        @Size(max = CODE_MAX)
        String stage,
    @Schema(description = "Beginn der Stufe.", example = "2026-10-13T22:41:07Z") @NotNull
        Instant startedAt,
    @Schema(
            description = "Ende der geltenden Frist der Stufe; null heißt: keine Frist vorgesehen.",
            example = "2026-10-14T00:41:07Z")
        @Nullable Instant deadline,
    @Schema(
            description =
                """
                Der Teilschritt, höchstens 40 Zeichen. Heute SESSION, \
                WAITING_FOR_IMPLEMENTATION oder RELEASE_CHECKS.""",
            example = "WAITING_FOR_IMPLEMENTATION")
        @NotBlank
        @Size(max = CODE_MAX)
        String step,
    @Schema(description = "true, wenn die Stufe die Umsetzung hält.", example = "false")
        boolean holdsImplementation) {

  /** Längengrenze von {@code stage} und {@code step} — die Spalten aus {@code V53}. */
  static final int CODE_MAX = 40;

  RunnerStufe toDomain() {
    return new RunnerStufe(cardNumber, stage, startedAt, deadline, step, holdsImplementation);
  }
}
