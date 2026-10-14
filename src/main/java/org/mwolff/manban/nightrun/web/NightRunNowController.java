package org.mwolff.manban.nightrun.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.mwolff.manban.common.web.api.Stabilitaet;
import org.mwolff.manban.nightrun.application.NightRunNowService;
import org.mwolff.manban.nightrun.application.TokenNotBoundForIngestException;
import org.mwolff.manban.nightrun.domain.ReadyBewertung;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Was der Runner jetzt tut und warum ein Paket wartet (Plan #1724): hier der Meldeweg der Bewertung
 * der Ready-Karten (Issue #1726, Vertrag V2).
 *
 * <p>Wie der Laufbericht im {@link NightRunIngestController} kommt das Zielprojekt aus der Bindung
 * des Tokens und nicht aus dem Aufruf. Diese beiden Controller sind die Stellen in {@code
 * nightrun}, die {@link KanbanPrincipal} lesen; Application und Domäne bekommen Primitive.
 */
@Tag(name = "Läufe (Runner)")
@RestController
class NightRunNowController {

  /** Obergrenze der bewerteten Karten einer Meldung. */
  static final int MAX_ENTRIES = 500;

  /** Längengrenze von {@code code} und {@code kind} — die Spalten aus {@code V54}. */
  static final int CODE_MAX = 40;

  /** Längengrenze des Wortlauts eines Grundes — die Spalte {@code text} aus {@code V54}. */
  static final int TEXT_MAX = 500;

  /** Längengrenze des Labels — die Spalte {@code label} aus {@code V54}. */
  static final int LABEL_MAX = 100;

  private final NightRunNowService service;

  NightRunNowController(NightRunNowService service) {
    this.service = service;
  }

  @Operation(
      summary = "Bewertung der Ready-Karten einliefern",
      description =
          """
          Meldet, wie der Runner die Karten in Ready bewertet: je Karte die Gründe, aus denen \
          er sie zurückstellt oder nicht zieht, und für die startbaren ihren Platz. Das \
          Zielprojekt kommt aus der Bindung des Projekt-Tokens (Header X-Kanban-Token); \
          melden darf, wer im Projekt Owner ist.\n\n\
          Je Projekt gilt nur die jüngste Bewertung: Eine Meldung ersetzt die gespeicherte \
          vollständig, eine gleich alte gilt als Wiederholung, eine ältere wird mit 409 \
          abgewiesen. code ist ein Code des Runners; ein unbekannter wird angenommen, und \
          die Anzeige zeigt dann text.""")
  @ApiResponse(responseCode = "200", description = "Die Bewertung ist gespeichert (STORED).")
  @ApiResponse(
      responseCode = "400",
      description =
          """
          Ungültige Eingabe: ein Feld fehlt oder verletzt seine Grenzen, oder eine Karte \
          steht doppelt (Details in fieldErrors). Ebenso: das Projekt-Token ist an kein \
          Projekt gebunden.""",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = "Die Person hinter dem Token ist im Projekt nicht Owner.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = "Das Projekt gibt es nicht, oder die Person ist dort kein Mitglied.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description = "Die gespeicherte Bewertung ist jünger als die gemeldete.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(stabilitaet = Stabilitaet.VERLAESSLICH)
  @PostMapping("/api/kanban/night-runs/ready-assessment")
  AssessmentResponse meldeBewertung(
      @Nullable Authentication authentication, @Valid @RequestBody AssessmentRequest request) {
    KanbanPrincipal principal = principal(authentication);
    Long projectId = principal.projectId();
    if (projectId == null) {
      throw new TokenNotBoundForIngestException();
    }
    service.meldeBewertung(principal.userId(), projectId, request.toDomain());
    return new AssessmentResponse(request.assessedAt(), Result.STORED);
  }

  private static KanbanPrincipal principal(@Nullable Authentication authentication) {
    if (authentication != null && authentication.getDetails() instanceof KanbanPrincipal p) {
      return p;
    }
    throw new TokenNotBoundForIngestException();
  }

  /** Was mit der Bewertung geschah. */
  @Schema(description = "STORED: als Bewertung des Projekts gespeichert.")
  enum Result {
    STORED
  }

  /** Antwort: der Zeitpunkt der Bewertung und was mit ihr geschah. */
  @Schema(description = "Ergebnis einer Einlieferung der Bewertung.")
  record AssessmentResponse(
      @Schema(description = "Zeitpunkt der Bewertung.", example = "2026-10-13T22:40:00Z")
          Instant assessedAt,
      @Schema(description = "Was mit der Bewertung geschah.", example = "STORED") Result result) {}

  /** Eine gemeldete Bewertung der Ready-Karten (Vertrag V2). */
  @Schema(description = "Die Bewertung der Ready-Karten; ersetzt die gespeicherte.")
  record AssessmentRequest(
      @Schema(description = "Wann der Runner bewertet hat.", example = "2026-10-13T22:40:00Z")
          @NotNull
          Instant assessedAt,
      @Schema(
              description = "Der Lauf, der bewertet hat; null bei einem Probelauf (--dry-run).",
              example = "2026-10-13T22:39:12Z")
          @Nullable Instant runStartedAt,
      @Schema(
              description = "Das Label, nach dem der Runner auswählt; höchstens 100 Zeichen.",
              example = "kit:nightrun")
          @NotBlank
          @Size(max = LABEL_MAX)
          String label,
      @Schema(description = "Je bewerteter Karte ein Eintrag, höchstens 500.")
          @NotNull
          @Size(max = MAX_ENTRIES)
          List<@Valid @NotNull AssessmentEntryRequest> entries) {

    /** Eine Karte darf nur einmal bewertet sein — sonst widersprächen sich zwei Einträge. */
    @Schema(hidden = true)
    @AssertTrue(message = "{nightrun.bewertung.karteDoppelt}")
    boolean isJedeKarteEinmal() {
      if (entries == null) {
        return true;
      }
      Set<Integer> gesehen = new HashSet<>();
      return entries.stream().filter(e -> e != null).allMatch(e -> gesehen.add(e.cardNumber()));
    }

    ReadyBewertung toDomain() {
      return new ReadyBewertung(
          assessedAt,
          runStartedAt,
          label,
          entries.stream().map(AssessmentEntryRequest::toDomain).toList());
    }
  }

  /** Eine bewertete Karte. */
  @Schema(description = "Eine bewertete Karte mit ihren Gründen.")
  record AssessmentEntryRequest(
      @Schema(description = "Projektweite Nummer der Karte.", example = "1695") int cardNumber,
      @Schema(
              description =
                  """
                  Platz unter den startbaren Karten in Board-Reihenfolge, ab 1; null bei einer \
                  Karte mit Grund.""",
              example = "1")
          @Nullable
          @Min(1)
          Integer position,
      @Schema(description = "Die Gründe; leer heißt: kein Hinderungsgrund.")
          @Nullable List<@Valid @NotNull AssessmentReasonRequest> reasons) {

    ReadyBewertung.Eintrag toDomain() {
      return new ReadyBewertung.Eintrag(
          cardNumber,
          position,
          reasons == null
              ? List.of()
              : reasons.stream().map(AssessmentReasonRequest::toDomain).toList());
    }
  }

  /** Ein Grund des Runners. */
  @Schema(
      description = "Ein Grund, aus dem der Runner die Karte zurückstellt oder nicht zieht.",
      example =
          """
          {"code":"DEPENDENCY","cardNumbers":[1698],"text":"Abhaengigkeit #1698 nicht erfuellt"}""")
  record AssessmentReasonRequest(
      @Schema(
              description =
                  """
                  Code des Grundes, höchstens 40 Zeichen. Heute NO_LABEL, NOT_A_WORK_PACKAGE, \
                  CLARIFY, PROTECTED_LABEL, PROTECTED_FILE, REVIEW_MISSING oder DEPENDENCY.""",
              example = "DEPENDENCY")
          @NotBlank
          @Size(max = CODE_MAX)
          String code,
      @Schema(
              description =
                  "Bei NOT_A_WORK_PACKAGE die Art der Karte: REQUIREMENT, IDEA, PLAN oder HUMAN.",
              example = "PLAN")
          @Nullable
          @Size(max = CODE_MAX)
          String kind,
      @Schema(description = "Bei DEPENDENCY die Karten, auf die gewartet wird.")
          @Nullable List<@NotNull Integer> cardNumbers,
      @Schema(description = "Bei PROTECTED_FILE die geschützten Pfade.")
          @Nullable List<@NotBlank String> paths,
      @Schema(
              description = "Wortlaut des Runners, höchstens 500 Zeichen.",
              example = "Abhaengigkeit #1698 nicht erfuellt")
          @NotBlank
          @Size(max = TEXT_MAX)
          String text) {

    ReadyBewertung.Grund toDomain() {
      return new ReadyBewertung.Grund(
          code,
          kind,
          cardNumbers == null ? List.of() : cardNumbers,
          paths == null ? List.of() : paths,
          text);
    }
  }
}
