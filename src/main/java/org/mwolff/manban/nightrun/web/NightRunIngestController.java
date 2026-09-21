package org.mwolff.manban.nightrun.web;

import static org.mwolff.manban.nightrun.web.NightRunController.COMMIT_HASH_MAX;
import static org.mwolff.manban.nightrun.web.NightRunController.MAX_ITEMS_PER_RUN;
import static org.mwolff.manban.nightrun.web.NightRunController.NO_WORK_REASON_MAX;
import static org.mwolff.manban.nightrun.web.NightRunController.TITLE_MAX;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.nightrun.application.NightRunService;
import org.mwolff.manban.nightrun.application.NightRunService.NewNightRun;
import org.mwolff.manban.nightrun.application.NightRunService.NewNightRunItem;
import org.mwolff.manban.nightrun.application.NightRunService.NightRunResult;
import org.mwolff.manban.nightrun.application.TokenNotBoundForIngestException;
import org.mwolff.manban.nightrun.domain.NightRunBudget;
import org.mwolff.manban.nightrun.domain.NightRunBudgetOrigin;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItemStage;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunLimits;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunStage;
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

  /**
   * Obergrenze der Stufen je Vorgang. Vier, weil die Kette genau vier Stufen kennt — {@code
   * PLAN|REVIEW|PAKETE|ABDECKUNG} (Plan #1110 E17), dieselben vier, die der {@code CHECK} auf
   * {@code night_run_item_stage.stage} zulässt. Eine fünfte Zeile wäre entweder eine Wiederholung
   * oder eine Stufe, die es nicht gibt.
   */
  static final int MAX_STAGES_PER_ITEM = 4;

  /**
   * Obergrenze der Feldnamen in {@code defaultFields} — bemessen an der Spalte {@code
   * budget_default_fields varchar(200)} aus {@code V37}, in die sie kommagetrennt geht: {@value
   * #MAX_DEFAULT_FIELDS} Namen zu je {@value #DEFAULT_FIELD_NAME_MAX} Zeichen samt Trennzeichen
   * passen hinein. Fünf Namen sind heute möglich; die Reserve lässt eine spätere Kit-Fassung durch,
   * statt ihren Lauf abzuweisen (E11).
   */
  static final int MAX_DEFAULT_FIELDS = 10;

  /** Längengrenze eines einzelnen Feldnamens; siehe {@link #MAX_DEFAULT_FIELDS}. */
  static final int DEFAULT_FIELD_NAME_MAX = 18;

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
        budget(request.budget()),
        request.items().stream().map(NightRunIngestController::item).toList());
  }

  private static NewNightRunItem item(IngestItemRequest request) {
    List<IngestStageRequest> stufen = request.stages();
    return new NewNightRunItem(
        request.cardNumber(),
        request.title(),
        request.state(),
        request.errorClass(),
        request.durationMs(),
        request.commitHash(),
        request.excerpt(),
        NightRunUsageRequest.toDomain(request.usage()),
        // Fehlende Stufen werden zur leeren Liste und nicht zu null: „dieser Vorgang hatte keine
        // Stufen" ist eine Aussage, und die Domaene fuehrt sie als Liste.
        stufen == null ? List.of() : stufen.stream().map(NightRunIngestController::stage).toList());
  }

  /**
   * Die gemeldeten Vorgaben als Domänenwert — oder {@code null}, wenn gar keine gemeldet wurden
   * („nicht angegeben", Plan #1110 E3).
   *
   * <p>Ein gemeldetes Budget ohne Feldliste trägt die <b>leere</b> Liste: „kein Feld kam aus den
   * Voreinstellungen" ist eine Aussage, und die Domäne führt sie deshalb nicht als {@code null}.
   */
  private static @Nullable NightRunBudget budget(@Nullable IngestBudgetRequest request) {
    if (request == null) {
      return null;
    }
    List<String> felder = request.defaultFields();
    return new NightRunBudget(
        request.planMin(),
        request.reviewMin(),
        request.paketeMin(),
        request.abdeckungMin(),
        request.kostenUsd(),
        request.origin(),
        felder == null ? List.of() : felder);
  }

  private static NightRunItemStage stage(IngestStageRequest request) {
    return new NightRunItemStage(
        request.stage(), request.durationMs(), NightRunUsageRequest.toDomain(request.usage()));
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
   * @param budget die Vorgaben, unter denen der Lauf angetreten ist (Issue #1113). Additiv und
   *     {@code @Nullable} aus demselben Grund wie {@code kind} und {@code noWorkReason}: Eine
   *     ältere Kit-Kopie kennt das Feld nicht und meldet unverändert weiter.
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
      @Nullable @Valid IngestBudgetRequest budget,
      @NotNull @Size(max = MAX_ITEMS_PER_RUN) List<@Valid @NotNull IngestItemRequest> items) {}

  /**
   * Ein gemeldetes Arbeitspaket.
   *
   * @param stages die Stufen der Kette, die dieser Vorgang durchlaufen hat (Issue #1113). Additiv
   *     und {@code @Nullable} wie {@code budget}; fehlt das Feld, hatte der Vorgang keine Stufen.
   */
  record IngestItemRequest(
      int cardNumber,
      @NotBlank @Size(max = TITLE_MAX) String title,
      @NotNull NightRunState state,
      @Nullable NightRunErrorClass errorClass,
      @Nullable Long durationMs,
      @Nullable @Size(max = COMMIT_HASH_MAX) String commitHash,
      @Nullable @Size(max = NightRunLimits.EXCERPT_MAX) String excerpt,
      @Nullable @Valid NightRunUsageRequest usage,
      @Nullable @Size(max = MAX_STAGES_PER_ITEM) List<@Valid @NotNull IngestStageRequest> stages) {}

  /**
   * Die gemeldeten Vorgaben eines Kettenlaufs (Issue #1113, Plan #1110 E2/E3).
   *
   * <p><b>Jedes Feld darf fehlen</b>, und keines trägt {@code @NotNull}: Der Vertrag bedient fremde
   * Kit-Stände, und ein Pflichtfeld wiese eine ältere Kopie ab — das kostete nicht eine Zeile,
   * sondern den ganzen Lauf (E11).
   *
   * <p><b>{@code defaultFields} wird nicht gegen eine feste Aufzählung geprüft</b> (E11). Ein Name,
   * den das Board nicht kennt, wird angenommen und erst bei der Anzeige ausgelassen. Begrenzt sind
   * nur Anzahl und Länge, und zwar allein deshalb, weil die verbundene Form in die Spalte {@code
   * budget_default_fields varchar(200)} passen muss: Ohne Grenze risse eine überlange Meldung dort
   * in einen Serverfehler statt in eine benannte Ablehnung.
   */
  record IngestBudgetRequest(
      @Nullable Integer planMin,
      @Nullable Integer reviewMin,
      @Nullable Integer paketeMin,
      @Nullable Integer abdeckungMin,
      @Nullable BigDecimal kostenUsd,
      @Nullable NightRunBudgetOrigin origin,
      @Nullable @Size(max = MAX_DEFAULT_FIELDS)
          List<@NotNull @Size(max = DEFAULT_FIELD_NAME_MAX) String> defaultFields) {}

  /**
   * Die gemeldeten Messwerte einer Stufe der Kette (Issue #1113).
   *
   * <p>{@code stage} ist Pflicht und keine Ausnahme von E11: Die Stufe ist die Identität des
   * Eintrags, und ein Eintrag ohne sie sagt nichts — er ließe sich weder anzeigen noch einer
   * Zeitvorgabe zuordnen.
   */
  record IngestStageRequest(
      @NotNull NightRunStage stage,
      @Nullable Long durationMs,
      @Nullable @Valid NightRunUsageRequest usage) {}
}
