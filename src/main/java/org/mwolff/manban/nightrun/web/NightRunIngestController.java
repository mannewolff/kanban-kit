package org.mwolff.manban.nightrun.web;

import static org.mwolff.manban.nightrun.web.NightRunController.COMMIT_HASH_MAX;
import static org.mwolff.manban.nightrun.web.NightRunController.MAX_ITEMS_PER_RUN;
import static org.mwolff.manban.nightrun.web.NightRunController.TITLE_MAX;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.nightrun.application.NightRunService;
import org.mwolff.manban.nightrun.application.NightRunService.NewNightRun;
import org.mwolff.manban.nightrun.application.NightRunService.NewNightRunItem;
import org.mwolff.manban.nightrun.application.NightRunService.NightRunResult;
import org.mwolff.manban.nightrun.application.TokenNotBoundForIngestException;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunLimits;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nimmt einen maschinell gemeldeten Nachtlauf entgegen (Issue #947, fachlich #927).
 *
 * <p>Der Weg ohne Menschen: Angemeldet wird mit einem projektgebundenen Zugriffstoken über die
 * {@code /api/kanban/**}-Strecke, ohne Sitzungs-Cookie. Das <b>Zielprojekt kommt aus der Bindung
 * des Tokens</b> und nicht aus dem Aufruf — so kann eine Meldung nur dort landen, wofür das Token
 * ausgestellt wurde, und ein Token kann nie mehr als sein Besitzer.
 *
 * <p><b>Ein Lauf je Aufruf</b>, ohne umschließende Liste: Eine Kette meldet den Lauf, an dem sie
 * gerade arbeitet, und meldet ihn im Verlauf mehrfach. Eine Liste legte nahe, mehrere Läufe auf
 * einmal zu schicken — das tut hier niemand.
 *
 * <p><b>Kein {@code unparsedSample}</b>: Der Ergebnisstand einer Kette ist strukturiert und kennt
 * keine ungedeuteten Zeilen. Das Feld gehört zum Weg über das Textprotokoll.
 *
 * <p>Diese Klasse ist die einzige Stelle in {@code nightrun}, die {@link KanbanPrincipal} liest;
 * die Application- und Domänenschicht kennen {@code accesstoken} nicht. Eine ArchUnit-Regel hält
 * das fest.
 */
@RestController
class NightRunIngestController {

  private final NightRunService service;

  NightRunIngestController(NightRunService service) {
    this.service = service;
  }

  @PostMapping("/api/kanban/night-runs")
  IngestResponse ingest(
      @Nullable Authentication authentication, @Valid @RequestBody IngestRequest request) {
    KanbanPrincipal principal = principal(authentication);
    Long projectId = principal.projectId();
    if (projectId == null) {
      throw new TokenNotBoundForIngestException();
    }
    NightRunResult ergebnis =
        service.ingest(principal.userId(), projectId, principal.tokenName(), run(request));
    return new IngestResponse(
        ergebnis.startedAt(), ergebnis.created() ? Outcome.CREATED : Outcome.REPLACED);
  }

  private static KanbanPrincipal principal(@Nullable Authentication authentication) {
    if (authentication != null && authentication.getDetails() instanceof KanbanPrincipal p) {
      return p;
    }
    throw new TokenNotBoundForIngestException();
  }

  private static NewNightRun run(IngestRequest request) {
    return new NewNightRun(
        request.startedAt(),
        request.mode(),
        request.durationMs(),
        request.processedCount(),
        request.skippedCount(),
        request.unparsedCount(),
        null,
        Boolean.TRUE.equals(request.complete()),
        NightRunUsageRequest.toDomain(request.usage()),
        request.items().stream().map(NightRunIngestController::item).toList());
  }

  private static NewNightRunItem item(IngestItemRequest request) {
    return new NewNightRunItem(
        request.cardNumber(),
        request.title(),
        request.state(),
        request.errorClass(),
        request.durationMs(),
        request.commitHash(),
        request.excerpt(),
        NightRunUsageRequest.toDomain(request.usage()));
  }

  /** Ob der Lauf angelegt oder ein vorhandener ersetzt wurde. */
  enum Outcome {
    CREATED,
    REPLACED
  }

  /** Antwort: der fachliche Schlüssel des Laufs und was mit ihm geschah. */
  record IngestResponse(Instant startedAt, Outcome outcome) {}

  /** Ein gemeldeter Lauf — der vollständige Stand, nicht eine Ergänzung. */
  record IngestRequest(
      @NotNull Instant startedAt,
      @NotNull NightRunMode mode,
      long durationMs,
      int processedCount,
      int skippedCount,
      int unparsedCount,
      @NotNull Boolean complete,
      @Nullable @Valid NightRunUsageRequest usage,
      @NotNull @Size(max = MAX_ITEMS_PER_RUN) List<@Valid @NotNull IngestItemRequest> items) {}

  /** Ein gemeldetes Arbeitspaket. */
  record IngestItemRequest(
      int cardNumber,
      @NotBlank @Size(max = TITLE_MAX) String title,
      @NotNull NightRunState state,
      @Nullable NightRunErrorClass errorClass,
      @Nullable Long durationMs,
      @Nullable @Size(max = COMMIT_HASH_MAX) String commitHash,
      @Nullable @Size(max = NightRunLimits.EXCERPT_MAX) String excerpt,
      @Nullable @Valid NightRunUsageRequest usage) {}
}
