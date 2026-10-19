package org.mwolff.manban.deployment.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.mwolff.manban.common.web.api.Stabilitaet;
import org.mwolff.manban.deployment.application.DeploymentService;
import org.mwolff.manban.deployment.application.Meldeergebnis;
import org.mwolff.manban.deployment.application.NeueMeldung;
import org.mwolff.manban.deployment.application.TokenNotBoundForDeploymentException;
import org.mwolff.manban.deployment.domain.Ausgang;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nimmt die Meldung eines Auslieferungsversuchs entgegen und nennt den zuletzt gemeldeten Stand
 * (Issue #1834, Plan #1831 E2, E3, E4, E7, fachlich #1784).
 *
 * <p>Der Weg ohne Menschen: Angemeldet wird mit einem board-gebundenen Zugriffstoken über die
 * {@code /api/kanban/**}-Strecke. Das <b>Zielprojekt kommt aus der Bindung des Tokens</b> und nicht
 * aus dem Aufruf; ein Token ohne Bindung wird mit {@code 409} abgewiesen (AK 13). Die
 * Anwendungsschicht bekommt nur Inhaber, Projekt und Token-Namen — {@link KanbanPrincipal} bleibt
 * in {@code web}, eine ArchUnit-Regel hält das fest (A1). Die paketprivaten Helfer aus {@code
 * kanbancompat} nutzt diese Klasse bewusst nicht (E4).
 *
 * <p>Beide Aufrufe sind verlässlich (E2): Das Melde-Kommando des Kits benutzt genau diese Form.
 */
@Tag(
    name = "Auslieferungen",
    description =
        """
        Auslieferungsversuche eines Projekts melden. Ein Auslieferungslauf meldet je Versuch \
        einmal Ausgang, Zeitpunkt, ausgelieferten Stand und die Commits seit dem vorigen \
        gemeldeten Stand; daraus rechnet der Leitstand die Platte „Auslieferung“.""")
@RestController
class DeploymentIngestController {

  /**
   * Längengrenze der Kennung — die Spalte {@code deployment_attempt.attempt_id} aus {@code V59}.
   */
  static final int ATTEMPT_ID_MAX = 200;

  /**
   * Längengrenze von Stand und Commit — die Spalten {@code revision} und {@code sha} aus {@code
   * V59}; ein SHA-256-Hash in Hex passt hinein.
   */
  static final int SHA_MAX = 64;

  /**
   * Obergrenze der Commits je Meldung. Die erste Meldung eines Projekts kann die ganze Geschichte
   * bis zum ausgelieferten Stand tragen; eine abgewiesene Meldung ginge ganz verloren (AK 15),
   * darum reichlich bemessen. Begrenzt ist sie trotzdem, damit eine Meldung nicht beliebig groß
   * werden kann.
   */
  static final int MAX_COMMITS = 20_000;

  private final DeploymentService service;

  DeploymentIngestController(DeploymentService service) {
    this.service = service;
  }

  @Operation(
      summary = "Auslieferungsversuch melden",
      description =
          """
          Meldet einen Auslieferungsversuch mit einem board-gebundenen Projekt-Token (Header \
          X-Kanban-Token). Das Zielprojekt ist das Projekt des Boards, an das das Token \
          gebunden ist; die Person hinter dem Token braucht dort das Recht, Karten anzulegen.\n\n\
          Eine Meldung je Versuch. Schlüssel ist attemptId: Die erste Meldung legt den Versuch \
          an (201 CREATED); dieselbe Meldung noch einmal ändert nichts (200 UNCHANGED), auch \
          wenn die Commits in anderer Reihenfolge stehen. Ein erneuter Versuch trägt eine neue \
          Kennung.\n\n\
          finishedAt ist der Zeitpunkt des Ausgangs, er zählt, nicht der Eingang. commits \
          nennt die Commits seit dem vorigen gemeldeten Stand (siehe „Letzten gemeldeten \
          Stand abrufen“), je mit Commit-Zeitpunkt; derselbe Commit darf in mehreren Versuchen \
          stehen und zählt trotzdem einmal.""")
  @ApiResponse(responseCode = "201", description = "Der Versuch ist neu gespeichert (CREATED).")
  @ApiResponse(
      responseCode = "200",
      description = "Der Versuch lag mit gleichem Inhalt schon vor; nichts hat sich geändert.")
  @ApiResponse(
      responseCode = "400",
      description =
          """
          Ungültige Eingabe: ein Feld fehlt oder verletzt seine Grenzen (Details in \
          fieldErrors), oder finishedAt liegt mehr als 5 Minuten nach der Serverzeit.""",
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
      description =
          """
          Das Token ist an kein Board gebunden, oder ein Versuch mit dieser Kennung wurde \
          schon mit anderem Inhalt gemeldet.""",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(stabilitaet = Stabilitaet.VERLAESSLICH)
  @PostMapping("/api/kanban/deployments")
  ResponseEntity<DeploymentResponse> melden(
      @Nullable Authentication authentication, @Valid @RequestBody DeploymentRequest request) {
    KanbanPrincipal principal = principal(authentication);
    Meldeergebnis ergebnis =
        service.melden(
            principal.userId(), projektId(principal), principal.tokenName(), meldung(request));
    return ergebnis == Meldeergebnis.CREATED
        ? ResponseEntity.status(HttpStatus.CREATED)
            .body(new DeploymentResponse(request.attemptId(), DeploymentStatus.CREATED))
        : ResponseEntity.ok(
            new DeploymentResponse(request.attemptId(), DeploymentStatus.UNCHANGED));
  }

  @Operation(
      summary = "Letzten gemeldeten Stand abrufen",
      description =
          """
          Nennt den Versuch des Projekts mit dem größten finishedAt, gleich welchen Ausgangs; \
          bei Gleichstand den zuletzt eingegangenen. Ein Auslieferungslauf bestimmt daraus die \
          Commits seit dem vorigen gemeldeten Stand — auch die eines gescheiterten Versuchs \
          sind schon gespeichert und müssen nicht erneut gemeldet werden. Ohne gemeldeten \
          Versuch 204 ohne Rumpf.""")
  @ApiResponse(responseCode = "200", description = "Der zuletzt gemeldete Stand.")
  @ApiResponse(
      responseCode = "204",
      description = "Für das Projekt ist noch kein Versuch gemeldet.",
      // Ohne Rumpf: Sonst erbte die Antwort das Schema des Rückgabetyps.
      content = @Content)
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
      description = "Das Token ist an kein Board gebunden.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(stabilitaet = Stabilitaet.VERLAESSLICH)
  @GetMapping("/api/kanban/deployments/latest")
  ResponseEntity<DeploymentLatestResponse> latest(@Nullable Authentication authentication) {
    KanbanPrincipal principal = principal(authentication);
    return service
        .letzterStand(principal.userId(), projektId(principal))
        .map(
            v ->
                ResponseEntity.ok(
                    new DeploymentLatestResponse(v.revision(), v.ausgang(), v.finishedAt())))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  private static KanbanPrincipal principal(@Nullable Authentication authentication) {
    if (authentication != null && authentication.getDetails() instanceof KanbanPrincipal p) {
      return p;
    }
    throw new TokenNotBoundForDeploymentException();
  }

  private static long projektId(KanbanPrincipal principal) {
    Long projectId = principal.projectId();
    if (projectId == null) {
      throw new TokenNotBoundForDeploymentException();
    }
    return projectId;
  }

  /**
   * Ein doppelt aufgeführter Commit ist derselbe Commit: Gleicher Inhalt heißt gleiche Menge der
   * Paare (E3), und eine zweite Zeile verletzte den Primärschlüssel {@code (attempt_ref, sha)}.
   */
  private static NeueMeldung meldung(DeploymentRequest request) {
    return new NeueMeldung(
        request.attemptId(),
        request.outcome(),
        request.finishedAt(),
        request.revision(),
        request.commits().stream()
            .map(c -> new NeueMeldung.Commit(c.sha(), c.committedAt()))
            .distinct()
            .toList());
  }

  /** Was mit der Meldung geschah. */
  enum DeploymentStatus {
    CREATED,
    UNCHANGED
  }

  /** Die Meldung eines Auslieferungsversuchs (Meldevertrag aus #1784). */
  @Schema(description = "Meldung eines Auslieferungsversuchs.")
  record DeploymentRequest(
      @Schema(
              description =
                  "Kennung des Versuchs; dieselbe Kennung zählt je Projekt nur einmal. Höchstens"
                      + " 200 Zeichen.",
              example = "deploy-2026-10-17-1")
          @NotBlank
          @Size(max = ATTEMPT_ID_MAX)
          String attemptId,
      @Schema(description = "Ausgang des Versuchs.", example = "SUCCESS") @NotNull Ausgang outcome,
      @Schema(
              description =
                  "Zeitpunkt des Ausgangs (ISO-8601); höchstens 5 Minuten nach der Serverzeit.",
              example = "2026-10-17T12:00:00Z")
          @NotNull
          Instant finishedAt,
      @Schema(
              description = "Der ausgelieferte Stand (Commit); höchstens 64 Zeichen.",
              example = "3f2c1a9e")
          @NotBlank
          @Size(max = SHA_MAX)
          String revision,
      @Schema(description = "Die Commits seit dem vorigen gemeldeten Stand, höchstens 20000.")
          @NotNull
          @Size(max = MAX_COMMITS)
          List<@Valid @NotNull DeploymentCommitRequest> commits) {}

  /** Ein gemeldeter Commit mit seinem Commit-Zeitpunkt. */
  @Schema(description = "Ein Commit des Versuchs.")
  record DeploymentCommitRequest(
      @Schema(description = "Kennung des Commits; höchstens 64 Zeichen.", example = "3f2c1a9e")
          @NotBlank
          @Size(max = SHA_MAX)
          String sha,
      @Schema(description = "Commit-Zeitpunkt (ISO-8601).", example = "2026-10-17T09:30:00Z")
          @NotNull
          Instant committedAt) {}

  /** Antwort: die Kennung und was mit dem Versuch geschah. */
  @Schema(description = "Ergebnis einer Meldung.")
  record DeploymentResponse(
      @Schema(description = "Kennung des Versuchs.", example = "deploy-2026-10-17-1")
          String attemptId,
      @Schema(description = "Was mit dem Versuch geschah.", example = "CREATED")
          DeploymentStatus status) {}

  /** Der zuletzt gemeldete Stand. */
  @Schema(description = "Der zuletzt gemeldete Versuch des Projekts.")
  record DeploymentLatestResponse(
      @Schema(description = "Der ausgelieferte Stand (Commit).", example = "3f2c1a9e")
          String revision,
      @Schema(description = "Ausgang des Versuchs.", example = "FAILURE") Ausgang outcome,
      @Schema(description = "Zeitpunkt des Ausgangs.", example = "2026-10-17T12:00:00Z")
          Instant finishedAt) {}
}
