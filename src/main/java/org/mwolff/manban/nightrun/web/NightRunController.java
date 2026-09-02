package org.mwolff.manban.nightrun.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.application.NightRunService;
import org.mwolff.manban.nightrun.application.NightRunService.NewNightRun;
import org.mwolff.manban.nightrun.application.NightRunService.NewNightRunItem;
import org.mwolff.manban.nightrun.application.NightRunService.NightRunResult;
import org.mwolff.manban.nightrun.application.NightRunService.NightRunView;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunLimits;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Nachtlauf-Auswertung an HTTP (Issue #723). Ausgewertet wird im Browser; hierher geht allein
 * die verdichtete Fassung (Plan #718, A1).
 *
 * <p>Es entstehen keine eigenen Exceptions: 404 und 403 liefert {@code requireOwner} im {@link
 * NightRunService}, 400 die Bean Validation über den {@code GlobalExceptionHandler} — die einzige
 * Mapping-Stelle des Projekts. Ein Eintrag in der {@code SecurityConfig} ist nicht nötig, {@code
 * /api/**} ist dort per Default {@code authenticated()}.
 */
@RestController
class NightRunController {

  /**
   * Obergrenze der Läufe je Anfrage. Bewusst <b>nicht</b> an {@code
   * manban.night-run.max-per-project} gekoppelt: Das Verdrängen überlässt Plan #718 (A14) dem
   * Service, und ein Protokoll kann mehr Läufe tragen als aufbewahrt werden — am 31.08. standen
   * vierzehn Aufrufe in einer Datei (A4). Wäre die Grenze die Aufbewahrung, bekäme der Owner für
   * ein größeres Protokoll 400 statt einer Antwort.
   */
  static final int MAX_RUNS_PER_REQUEST = 100;

  /**
   * Obergrenze der Arbeitspakete je Lauf, bemessen wie {@code
   * ProjectIdeaController#MAX_IDEAS_PER_BATCH}.
   */
  static final int MAX_ITEMS_PER_RUN = 200;

  /** Titelgrenze wie an der Quelle {@code card.title}; ein Schnappschuss kann nie länger sein. */
  private static final int TITLE_MAX = 300;

  /** Ein Commit-Hash ist höchstens ein vollständiger SHA-1 (40 Zeichen), wie in {@code V29}. */
  private static final int COMMIT_HASH_MAX = 40;

  private final NightRunService runs;

  NightRunController(NightRunService runs) {
    this.runs = runs;
  }

  /**
   * Nimmt die im Browser erzeugten Auswertungen entgegen und meldet je Lauf, ob er angelegt wurde
   * oder schon vorlag — in Anfragereihenfolge, ein Eintrag je übergebenem Lauf.
   *
   * <p>Antwort 200 statt 201: Sie meldet auch schon vorliegende Läufe, und beim wiederholten
   * Hochladen desselben Protokolls entsteht gar keine Ressource.
   */
  @PostMapping("/api/projects/{projectId}/night-runs")
  List<NightRunResult> submit(
      @AuthenticationPrincipal Long userId,
      @PathVariable long projectId,
      @Valid @RequestBody SubmitNightRunsRequest request) {
    return runs.submit(
        userId, projectId, request.runs().stream().map(NightRunController::run).toList());
  }

  /** Die aufbewahrten Läufe des Projekts, neueste zuerst, jeder mit seinen Arbeitspaketen. */
  @GetMapping("/api/projects/{projectId}/night-runs")
  List<NightRunView> list(@AuthenticationPrincipal Long userId, @PathVariable long projectId) {
    return runs.list(userId, projectId);
  }

  /**
   * Je Fehlerklasse die Zahl der aufbewahrten Läufe, in denen sie mindestens einmal vorkam (Plan
   * #718, A12). Eigener Endpunkt statt eines Felds der Listenantwort: Die Zahl ist eine Aggregation
   * über alle Läufe, und die Liste müsste sie sonst bei jedem Abruf mitschleppen.
   */
  @GetMapping("/api/projects/{projectId}/night-runs/error-class-counts")
  Map<NightRunErrorClass, Long> errorClassCounts(
      @AuthenticationPrincipal Long userId, @PathVariable long projectId) {
    return runs.countRunsByErrorClass(userId, projectId);
  }

  private static NewNightRun run(NightRunRequest request) {
    return new NewNightRun(
        request.startedAt(),
        request.mode(),
        request.durationMs(),
        request.processedCount(),
        request.skippedCount(),
        request.unparsedCount(),
        request.unparsedSample(),
        request.items().stream().map(NightRunController::item).toList());
  }

  private static NewNightRunItem item(NightRunItemRequest request) {
    return new NewNightRunItem(
        request.cardNumber(),
        request.title(),
        request.state(),
        request.errorClass(),
        request.durationMs(),
        request.commitHash(),
        request.excerpt());
  }

  /**
   * Eine leere Liste ist eine Fehleingabe und keine leere Erfolgsantwort ({@code @NotEmpty} → 400)
   * — dasselbe Verhalten wie bei {@code ideas/batch}. Der Browser sendet bei einem Protokoll aus
   * lauter Probeläufen gar nicht erst.
   */
  record SubmitNightRunsRequest(
      @NotEmpty @Size(max = MAX_RUNS_PER_REQUEST) List<@Valid @NotNull NightRunRequest> runs) {}

  /**
   * Ein einzuliefernder Lauf. Die Liste der Arbeitspakete trägt {@code @Valid} an ihren Elementen —
   * ohne das blieben die Feldgrenzen der Pakete ungeprüft. Leer darf sie sein: Ein harter Abbruch
   * hinterlässt einen Lauf ohne Arbeitspaket.
   */
  record NightRunRequest(
      @NotNull Instant startedAt,
      @NotNull NightRunMode mode,
      long durationMs,
      int processedCount,
      int skippedCount,
      int unparsedCount,
      @Nullable @Size(max = NightRunLimits.EXCERPT_MAX) String unparsedSample,
      @NotNull @Size(max = MAX_ITEMS_PER_RUN) List<@Valid @NotNull NightRunItemRequest> items) {}

  /** Ein einzulieferndes Arbeitspaket. */
  record NightRunItemRequest(
      int cardNumber,
      @NotBlank @Size(max = TITLE_MAX) String title,
      @NotNull NightRunState state,
      @Nullable NightRunErrorClass errorClass,
      @Nullable Long durationMs,
      @Nullable @Size(max = COMMIT_HASH_MAX) String commitHash,
      @Nullable @Size(max = NightRunLimits.EXCERPT_MAX) String excerpt) {}
}
