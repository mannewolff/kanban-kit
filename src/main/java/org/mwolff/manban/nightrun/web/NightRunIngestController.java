package org.mwolff.manban.nightrun.web;

import static org.mwolff.manban.nightrun.web.NightRunController.COMMIT_HASH_MAX;
import static org.mwolff.manban.nightrun.web.NightRunController.MAX_ITEMS_PER_RUN;
import static org.mwolff.manban.nightrun.web.NightRunController.NO_WORK_REASON_MAX;
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
import org.mwolff.manban.nightrun.domain.NightRunKind;
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
 * <p><b>Dieselbe Strecke trägt die interaktive Sitzung</b> (Issue #1012, Plan #1007 E12/E17): Der
 * Rumpf ist bis auf das optionale Feld {@code kind} derselbe. Fehlt es, gilt {@link
 * NightRunKind#NIGHT} — die Erweiterung ist additiv, und eine ältere Kit-Kopie meldet unverändert
 * weiter. Ein zweiter Endpunkt mit gleichem Rumpf liefe mit der Zeit auseinander.
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
        service.ingest(
            principal.userId(), projectId, principal.tokenName(), kind(request), run(request));
    return new IngestResponse(
        ergebnis.startedAt(), ergebnis.created() ? Outcome.CREATED : Outcome.REPLACED);
  }

  /** Fehlt die Gattung, ist die Meldung ein Nachtlauf — so meldet jede ältere Kit-Kopie. */
  private static NightRunKind kind(IngestRequest request) {
    NightRunKind gemeldet = request.kind();
    return gemeldet == null ? NightRunKind.NIGHT : gemeldet;
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
        request.noWorkReason(),
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

  /**
   * Ein gemeldeter Lauf — der vollständige Stand, nicht eine Ergänzung.
   *
   * @param mode Pflichtfeld; nimmt seit {@code V34} auch {@link NightRunMode#INTERACTIVE} an (E23)
   * @param kind Gattung des Eintrags; fehlt sie, gilt {@link NightRunKind#NIGHT} (Issue #1012).
   *     Bewusst optional und nicht {@code @NotNull}: Eine ältere Kit-Kopie kennt das Feld nicht,
   *     und ihre Meldung soll weiterhin ankommen statt an der Prüfung zu scheitern.
   * @param noWorkReason Grund, warum nichts abzuarbeiten war (Issue #1068). Additiv und
   *     {@code @Nullable} aus demselben Grund wie {@code kind} (Issue #1012): Eine ältere Kit-Kopie
   *     kennt das Feld nicht und meldet unverändert weiter; ihr Lauf bekommt dann den Rückfalltext
   *     des Servers statt einer abgewiesenen Meldung. Ob der Wert überhaupt am Lauf landet,
   *     entscheidet der Dienst — gemeldet heißt nicht gesetzt.
   */
  record IngestRequest(
      @NotNull Instant startedAt,
      @NotNull NightRunMode mode,
      @Nullable NightRunKind kind,
      long durationMs,
      int processedCount,
      int skippedCount,
      int unparsedCount,
      @NotNull Boolean complete,
      @Nullable @Valid NightRunUsageRequest usage,
      @Nullable @Size(max = NO_WORK_REASON_MAX) String noWorkReason,
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
