package org.mwolff.manban.codeanalyse.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.codeanalyse.application.GemeldeteMessung;
import org.mwolff.manban.codeanalyse.application.IdempotencyKeyMismatchException;
import org.mwolff.manban.codeanalyse.application.MessungService;
import org.mwolff.manban.codeanalyse.application.TokenNotBoundForMessungException;
import org.mwolff.manban.codeanalyse.domain.Messung;
import org.mwolff.manban.codeanalyse.domain.Zustand;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.mwolff.manban.common.web.api.Stabilitaet;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nimmt die Messung der Codeanalyse zu einer Veröffentlichung entgegen (Issue #2277, Plan #2272
 * Vertrag V1, E3, E4, E5, E8, E13; fachlich #2271 AK 9, AK 16).
 *
 * <p>Der Weg ohne Menschen: Angemeldet wird mit einem board-gebundenen Zugriffstoken über die
 * {@code /api/kanban/**}-Strecke. Das <b>Projekt kommt aus der Bindung des Tokens</b> und nicht aus
 * dem Aufruf; ein Token ohne Bindung wird mit {@code 409} abgewiesen, und nichts wird gespeichert.
 * {@link KanbanPrincipal} bleibt in {@code web}, eine ArchUnit-Regel hält das fest; die Helfer
 * {@code principal} und {@code projektId} sind eine bewusste kleine Kopie (E13).
 *
 * <p>Die Prüfung der Eingabe (E8) sitzt in der Bean Validation. Was zwei Felder zugleich betrifft —
 * die Zählregel aus AK 16 — und die Zahlen beider Zustände prüft die Klassenregel {@link
 * Zaehlregel}: Nur so nennt {@code fieldErrors} die Felder mit ihren Namen aus V1, auch {@code
 * final}, das in Java kein Name sein kann.
 */
@Tag(
    name = "Codeanalyse",
    description =
        """
        Messungen der Codeanalyse eines Projekts melden. Das Workflow-Kit meldet je \
        Veröffentlichung einmal die Funde vor der Codeanalyse und im Endzustand; daraus rechnet \
        der Leitstand die Platte „Codeanalyse“.""")
@RestController
class MessungIngestController {

  /** Längengrenze der Kennungen — die Spalten {@code published_commit}, {@code base_commit}. */
  static final int COMMIT_MAX = 64;

  /** Längengrenze der Fassung des Kits — die Spalte {@code kit_version} aus {@code V83}. */
  static final int KIT_VERSION_MAX = 50;

  /** Längengrenze des Grundes — die Spalte {@code reason} aus {@code V83}. */
  static final int REASON_MAX = 100;

  /** Ein Grund ist ein Kurztext; auch ein unbekannter wird angenommen (E8, AK 3). */
  static final String REASON_MUSTER = "[a-z0-9-]*";

  /** Kopf, der {@code publishedCommit} wiederholt (V1, E4). */
  static final String IDEMPOTENCY_KEY = "Idempotency-Key";

  private static final String PROBLEM_400 =
      """
      Ungültige Eingabe: ein Feld fehlt oder verletzt seine Grenzen (Details in fieldErrors), \
      etwa eine zählende Messung ohne before oder final oder eine nicht zählende ohne reason; \
      oder publishedAt liegt mehr als 5 Minuten nach der Serverzeit; oder der Kopf \
      Idempotency-Key weicht von publishedCommit ab.""";

  private final MessungService service;

  MessungIngestController(MessungService service) {
    this.service = service;
  }

  @Operation(
      summary = "Messung der Codeanalyse melden",
      description =
          """
          Meldet die Messung der Codeanalyse zu einer Veröffentlichung mit einem \
          board-gebundenen Projekt-Token (Header X-Kanban-Token). Das Projekt ist das Projekt des \
          Boards, an das das Token gebunden ist; die Person hinter dem Token braucht dort das \
          Recht, Karten anzulegen.

          Eine Meldung je Veröffentlichung. Schlüssel ist publishedCommit je Projekt: Es gilt \
          die erste Meldung; jede weitere mit demselben publishedCommit ändert nichts, auch mit \
          anderem Inhalt. Beide Aufrufe antworten mit 200 und publishedCommit und counts der \
          gespeicherten ersten Meldung. Der Kopf Idempotency-Key ist freiwillig; ist er gesetzt, \
          muss er publishedCommit gleichen.

          publishedAt ist der Zeitpunkt der Veröffentlichung, er bestimmt die Kalenderwoche \
          (Montag bis Sonntag, UTC), nicht der Eingang. Eine zählende Messung (counts true) \
          braucht before und final vollständig; eine nicht zählende (counts false) braucht \
          reason, ihre Zustände dürfen fehlen. Ein reason bei einer zählenden Messung wird nicht \
          gespeichert. Bekannte Gründe sind no-measurement, missing-before, missing-final, \
          cards-removed, incomplete und not-attributable; ein anderer Grund wird angenommen und \
          gezeigt, wie er gemeldet wurde. Eine abgewiesene Meldung wird nicht gespeichert, eine \
          berichtigte derselben Veröffentlichung wird darum angenommen.""")
  @ApiResponse(
      responseCode = "200",
      description = "Die Messung ist gespeichert, oder sie lag schon vor; die erste gilt.")
  @ApiResponse(
      responseCode = "400",
      description = PROBLEM_400,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = "Die Person hinter dem Token darf im Projekt keine Karten anlegen.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = "Das Projekt gibt es nicht, oder die Person ist dort kein Mitglied mehr.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description = "Das Token ist an kein Board gebunden; nichts wird gespeichert.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(stabilitaet = Stabilitaet.VERLAESSLICH)
  @PostMapping("/api/kanban/code-analysis-measurements")
  ResponseEntity<CodeAnalysisMeasurementResponse> melden(
      @Nullable Authentication authentication,
      @Parameter(
              in = ParameterIn.HEADER,
              description = "Freiwillig; ist er gesetzt, muss er publishedCommit gleichen.",
              example = "3f2c1a9e")
          @RequestHeader(name = IDEMPOTENCY_KEY, required = false)
          @Nullable String idempotencyKey,
      @Valid @RequestBody CodeAnalysisMeasurementRequest request) {
    KanbanPrincipal principal = principal(authentication);
    long projektId = projektId(principal);
    if (idempotencyKey != null && !idempotencyKey.equals(request.publishedCommit())) {
      throw new IdempotencyKeyMismatchException();
    }
    Messung gespeichert =
        service.melden(principal.userId(), projektId, principal.tokenName(), meldung(request));
    return ResponseEntity.ok(
        new CodeAnalysisMeasurementResponse(gespeichert.publishedCommit(), gespeichert.zaehlt()));
  }

  private static KanbanPrincipal principal(@Nullable Authentication authentication) {
    if (authentication != null && authentication.getDetails() instanceof KanbanPrincipal p) {
      return p;
    }
    throw new TokenNotBoundForMessungException();
  }

  private static long projektId(KanbanPrincipal principal) {
    Long projectId = principal.projectId();
    if (projectId == null) {
      throw new TokenNotBoundForMessungException();
    }
    return projectId;
  }

  /** Die geprüfte Anfrage; die Bean Validation hat jedes Pflichtfeld schon belegt. */
  private static GemeldeteMessung meldung(CodeAnalysisMeasurementRequest request) {
    return new GemeldeteMessung(
        request.publishedCommit(),
        request.publishedAt(),
        request.baseCommit(),
        request.kitVersion(),
        request.counts(),
        request.reason(),
        request.cards(),
        zustand(request.before()),
        zustand(request.endzustand()));
  }

  private static @Nullable Zustand zustand(@Nullable CodeAnalysisStateRequest state) {
    return state == null
        ? null
        : new Zustand(
            state.bugs(), state.vulnerabilities(), state.codeSmells(), state.changedLines());
  }

  /** Die Meldung einer Messung (Vertrag V1). */
  @Schema(description = "Meldung einer Messung der Codeanalyse zu einer Veröffentlichung.")
  @Zaehlregel
  record CodeAnalysisMeasurementRequest(
      @Schema(
              description =
                  "Zeitpunkt der Veröffentlichung (ISO-8601, UTC); höchstens 5 Minuten nach der"
                      + " Serverzeit.",
              example = "2026-11-26T11:00:00Z")
          @NotNull
          Instant publishedAt,
      @Schema(
              description =
                  "Kennung des veröffentlichten Stands; je Projekt zählt dieselbe Kennung nur"
                      + " einmal. Höchstens 64 Zeichen.",
              example = "3f2c1a9e")
          @NotBlank
          @Size(max = COMMIT_MAX)
          String publishedCommit,
      @Schema(
              description = "Stand, gegen den gemessen wurde; höchstens 64 Zeichen.",
              example = "1b9c9a24")
          @NotBlank
          @Size(max = COMMIT_MAX)
          String baseCommit,
      @Schema(description = "Fassung des Workflow-Kits; höchstens 50 Zeichen.", example = "3.21.1")
          @NotBlank
          @Size(max = KIT_VERSION_MAX)
          String kitVersion,
      @Schema(description = "Ob die Messung zählt.", example = "true") @NotNull Boolean counts,
      @Schema(
              description =
                  """
                  Warum die Messung nicht zählt; Pflicht bei counts false, bei counts true ohne \
                  Wirkung. Kleinbuchstaben, Ziffern und Bindestrich, höchstens 100 Zeichen.""",
              example = "cards-removed")
          @Size(max = REASON_MAX)
          @Pattern(regexp = REASON_MUSTER)
          @Nullable String reason,
      @Schema(description = "Zahl der Karten der Veröffentlichung.", example = "3") @NotNull @Min(0)
          Integer cards,
      @Schema(
              description =
                  "Zustand vor der Codeanalyse; Pflicht bei counts true, sonst auch null.")
          @Nullable CodeAnalysisStateRequest before,
      @Schema(
              description =
                  "Zustand des veröffentlichten Stands; Pflicht bei counts true, sonst auch null.")
          @JsonProperty("final")
          @Nullable CodeAnalysisStateRequest endzustand) {}

  /**
   * Ein Zustand einer Messung. Die Zahlen prüft {@link Zaehlregel}, damit {@code fieldErrors} sie
   * unter {@code before.*} und {@code final.*} nennt.
   */
  @Schema(description = "Funde je Art und geänderte Zeilen eines Zustands; keine Zahl negativ.")
  record CodeAnalysisStateRequest(
      @Schema(
              description = "Funde der Art Fehler.",
              example = "4",
              minimum = "0",
              requiredMode = Schema.RequiredMode.REQUIRED)
          Integer bugs,
      @Schema(
              description = "Funde der Art Sicherheitslücke.",
              example = "0",
              minimum = "0",
              requiredMode = Schema.RequiredMode.REQUIRED)
          Integer vulnerabilities,
      @Schema(
              description = "Funde der Art Wartbarkeitsmangel.",
              example = "1",
              minimum = "0",
              requiredMode = Schema.RequiredMode.REQUIRED)
          Integer codeSmells,
      @Schema(
              description = "Geänderte Zeilen.",
              example = "2200",
              minimum = "0",
              requiredMode = Schema.RequiredMode.REQUIRED)
          Integer changedLines) {}

  /** Antwort: die gespeicherte erste Meldung dieser Veröffentlichung (E3). */
  @Schema(description = "Die gespeicherte Messung dieser Veröffentlichung; die erste gilt.")
  record CodeAnalysisMeasurementResponse(
      @Schema(description = "Kennung des veröffentlichten Stands.", example = "3f2c1a9e")
          String publishedCommit,
      @Schema(description = "Ob die gespeicherte Messung zählt.", example = "true")
          boolean counts) {}
}
