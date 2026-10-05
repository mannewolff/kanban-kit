package org.mwolff.manban.nightrun.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.nightrun.application.NightRunProgressService;
import org.mwolff.manban.nightrun.application.NightRunService;
import org.mwolff.manban.nightrun.application.NightRunService.NewNightRun;
import org.mwolff.manban.nightrun.application.NightRunService.NewNightRunItem;
import org.mwolff.manban.nightrun.application.NightRunService.NightRunResult;
import org.mwolff.manban.nightrun.application.NightRunService.NightRunView;
import org.mwolff.manban.nightrun.domain.NachtFreigabe;
import org.mwolff.manban.nightrun.domain.NachtFreigabe.Startstation;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunLimits;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.ProgressStage;
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
 * <p>Seit Issue #948 nimmt der Weg den gemeldeten Kostenwert mit — je Lauf und je Arbeitspaket. Die
 * drei Mengen (Eingabe, Ausgabe, Zwischenspeicher) bleiben ihm fremd: Der Browser misst sie nicht,
 * und ein Feld, das nie einen Wert trägt, wäre eine leere Zusage.
 *
 * <p>Es entstehen keine eigenen Exceptions: 404 und 403 liefert {@code requireOwner} im {@link
 * NightRunService}, 400 die Bean Validation über den {@code GlobalExceptionHandler} — die einzige
 * Mapping-Stelle des Projekts. Ein Eintrag in der {@code SecurityConfig} ist nicht nötig, {@code
 * /api/**} ist dort per Default {@code authenticated()}.
 */
@Tag(name = "Läufe (Runner)")
@RestController
class NightRunController {

  /** Projektkennung als Pfadparameter, gemeinsam für alle Aufrufe dieser Klasse. */
  static final String BESCHREIBUNG_PROJEKT_ID = "Kennung des Projekts.";

  /** Wer die Läufe eines Projekts lesen darf — gleich für alle lesenden Aufrufe des Moduls. */
  static final String LESERECHT =
      "Lesen darf, wer im Projekt die Rolle Owner hat; ein Plattform-Admin nur, wenn das Projekt"
          + " am Plattform-Leitstand teilnimmt.";

  /** 403 der lesenden Aufrufe. */
  static final String LESEN_VERBOTEN =
      "Der Aufrufer ist Mitglied, aber nicht Owner — oder Plattform-Admin, und das Projekt nimmt"
          + " nicht am Plattform-Leitstand teil.";

  /** 404, wenn das Projekt fehlt oder der Aufrufer dort nichts zu suchen hat. */
  static final String PROJEKT_UNBEKANNT =
      "Das Projekt gibt es nicht, oder der Aufrufer ist dort kein Mitglied.";

  /**
   * Obergrenze der Läufe je Anfrage. Bewusst <b>nicht</b> an {@code
   * manban.nightrun.max-per-project} gekoppelt: Das Verdrängen überlässt Plan #718 (A14) dem
   * Service, und ein Protokoll kann mehr Läufe tragen als aufbewahrt werden — am 31.08. standen
   * vierzehn Aufrufe in einer Datei (A4). Wäre die Grenze die Aufbewahrung, bekäme der Owner für
   * ein größeres Protokoll 400 statt einer Antwort.
   */
  static final int MAX_RUNS_PER_REQUEST = 100;

  /**
   * Obergrenze der Arbeitspakete je Lauf, bemessen wie {@code CardController#MAX_CARDS_PER_BATCH}.
   */
  static final int MAX_ITEMS_PER_RUN = 200;

  /** Titelgrenze wie an der Quelle {@code card.title}; ein Schnappschuss kann nie länger sein. */
  static final int TITLE_MAX = 300;

  /** Ein Commit-Hash ist höchstens ein vollständiger SHA-1 (40 Zeichen), wie in {@code V29}. */
  static final int COMMIT_HASH_MAX = 40;

  /**
   * Obergrenze des Grundes, warum ein Lauf nichts abgearbeitet hat (Issue #1068) — dieselbe Laenge
   * wie {@link #TITLE_MAX} und wie die Spalte {@code night_run.no_work_reason} aus {@code V35}. Der
   * Wert ist ein Satz fuer die Anzeige; lange Texte fuehrt {@code unparsedSample}.
   */
  static final int NO_WORK_REASON_MAX = 300;

  /**
   * Obergrenze des Grundes, warum ein Lauf hart abgebrochen ist (Issue #1142) — die Laenge der
   * Spalte {@code night_run.abort_reason} aus {@code V42}, und damit {@link
   * NightRunLimits#EXCERPT_MAX} statt der 300 von {@link #NO_WORK_REASON_MAX} (Plan #1139 E4): Ein
   * Grund ohne Arbeit ist ein Satz fuer die Anzeige, ein Abbruchgrund fuehrt Dateilisten.
   */
  static final int ABORT_REASON_MAX = NightRunLimits.EXCERPT_MAX;

  private final NightRunService runs;
  private final NightRunProgressService fortschritt;

  NightRunController(NightRunService runs, NightRunProgressService fortschritt) {
    this.runs = runs;
    this.fortschritt = fortschritt;
  }

  /**
   * Nimmt die im Browser erzeugten Auswertungen entgegen und meldet je Lauf, ob er angelegt wurde
   * oder schon vorlag — in Anfragereihenfolge, ein Eintrag je übergebenem Lauf.
   *
   * <p>Antwort 200 statt 201: Sie meldet auch schon vorliegende Läufe, und beim wiederholten
   * Hochladen desselben Protokolls entsteht gar keine Ressource.
   */
  @Operation(
      summary = "Ausgewertete Läufe hochladen",
      description =
          "Nimmt Läufe entgegen, die der Browser aus einem Textprotokoll des Runners gedeutet hat"
              + " („Protokoll einlesen“ auf der Runner-Seite). Ein Lauf ist eine Sitzung eines"
              + " Agenten, die Karten abarbeitet; seine Arbeitspakete (items) sind die Karten, die"
              + " er angefasst hat, mit Ausgang (state) und gegebenenfalls Fehlerklasse. Das"
              + " Protokoll selbst verlässt den Browser nicht.\n\n"
              + "Schlüssel eines Laufs ist startedAt: Ein Lauf, den das Projekt schon kennt, bleibt"
              + " unangetastet und wird als schon vorliegend gemeldet — wiederholtes Hochladen"
              + " desselben Protokolls ist darum folgenlos. Die Antwort nennt je übergebenem Lauf,"
              + " in Anfragereihenfolge, ob er angelegt wurde (created). Verlangt die Rolle Owner"
              + " im Projekt; ein Plattform-Admin darf ebenfalls.\n\n"
              + "Den strukturierten Weg ohne Browser, mit Projekt-Token, bietet POST"
              + " /api/kanban/night-runs.")
  @ApiResponse(
      responseCode = "200",
      description =
          "Je Lauf, ob er angelegt wurde oder schon vorlag — auch für bekannte Läufe 200.")
  @ApiResponse(
      responseCode = "400",
      description =
          "Ungültige Eingabe: leere Liste, mehr als 100 Läufe oder 200 Arbeitspakete je Lauf,"
              + " ein Pflichtfeld fehlt oder ein Feld verletzt seine Grenzen (Details in"
              + " fieldErrors).",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = "Der Aufrufer ist Mitglied, aber nicht Owner.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = PROJEKT_UNBEKANNT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @PostMapping("/api/projects/{projectId}/night-runs")
  List<NightRunResult> submit(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_PROJEKT_ID, example = "7") @PathVariable long projectId,
      @Valid @RequestBody SubmitNightRunsRequest request) {
    return runs.submit(
        userId, projectId, request.runs().stream().map(NightRunController::run).toList());
  }

  /** Die aufbewahrten Läufe des Projekts, neueste zuerst, jeder mit seinen Arbeitspaketen. */
  @Operation(
      summary = "Läufe des Projekts lesen",
      description =
          "Liefert die aufbewahrten Nachtläufe des Projekts, neueste zuerst, jeden mit seinen"
              + " Arbeitspaketen. Ein Lauf ist eine Sitzung eines Agenten, die Karten abarbeitet;"
              + " ein Nachtlauf ist ein Lauf des Runners ohne Menschen (kind NIGHT). Interaktive"
              + " Sitzungen (kind INTERACTIVE) stehen nicht in dieser Liste. mode nennt die Art"
              + " — IMPLEMENTATION setzt Arbeitspakete aus Ready um,"
              + " REVIEW begutachtet Kandidaten, CHAIN ist eine Kette: ein Lauf, der eine"
              + " fachliche Anforderung über die Stufen PLAN, REVIEW, PAKETE und ABDECKUNG"
              + " führt. Je Projekt bleibt nur eine begrenzte Zahl Läufe aufbewahrt; ältere werden"
              + " verdrängt. "
              + LESERECHT)
  @ApiResponse(responseCode = "200", description = "Die Läufe, neueste zuerst.")
  @ApiResponse(
      responseCode = "403",
      description = LESEN_VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = PROJEKT_UNBEKANNT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/projects/{projectId}/night-runs")
  List<NightRunView> list(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_PROJEKT_ID, example = "7") @PathVariable
          long projectId) {
    return runs.list(userId, projectId);
  }

  /**
   * Je Fehlerklasse die Zahl der aufbewahrten Läufe, in denen sie mindestens einmal vorkam (Plan
   * #718, A12). Eigener Endpunkt statt eines Felds der Listenantwort: Die Zahl ist eine Aggregation
   * über alle Läufe, und die Liste müsste sie sonst bei jedem Abruf mitschleppen.
   */
  @Operation(
      summary = "Fehlerklassen zählen",
      description =
          "Liefert je Fehlerklasse die Zahl der aufbewahrten Nachtläufe, in denen sie mindestens"
              + " einmal vorkam; interaktive Sitzungen zählen nicht mit. Die Fehlerklasse nennt"
              + " den Grund, warum ein Arbeitspaket nicht"
              + " grün endete — etwa CHECKS_RED (Pflichtprüfungen rot), DEPENDENCY_UNMET"
              + " (Voraussetzung offen) oder HARD_ABORT (Lauf hart abgebrochen). Klassen ohne"
              + " Vorkommen fehlen in der Antwort. "
              + LESERECHT)
  @ApiResponse(responseCode = "200", description = "Fehlerklasse → Zahl der Läufe.")
  @ApiResponse(
      responseCode = "403",
      description = LESEN_VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = PROJEKT_UNBEKANNT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/projects/{projectId}/night-runs/error-class-counts")
  Map<NightRunErrorClass, Long> errorClassCounts(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_PROJEKT_ID, example = "7") @PathVariable
          long projectId) {
    return runs.countRunsByErrorClass(userId, projectId);
  }

  /**
   * Was heute Nacht ansteht (Issue #1454, Plan #1447 E4): die freigegebenen, noch nicht
   * übernommenen Karten des Projekts über alle Boards. Ohne {@code @ApiVertrag}: Der Aufruf bedient
   * nur die eigene Runner-Seite.
   */
  @Operation(
      summary = "Heute Nacht lesen",
      description =
          "Liefert die Karten des Projekts, die für die nächste Nacht anstehen: fachliche"
              + " Anforderungen ([Fachlich]) und Pläne ([Plan]) aller nicht archivierten Boards,"
              + " die zur Übernahme durch den nächsten Runner freigegeben sind (Label kit:night)"
              + " und die noch kein Runner übernommen hat. Je Karte kommen Nummer, Titel, Board,"
              + " die Startstation der Kette (FACHPLAN oder PLAN), ihr Ziel (aus ziel:*, ohne"
              + " Angabe PAKETE, mit kit:durchziehen mindestens UMSETZUNG) und die gewählte"
              + " Prüferzahl aus planreview:*. Sortiert nach Kartennummer; leer, wenn nichts"
              + " freigegeben ist. "
              + LESERECHT)
  @ApiResponse(responseCode = "200", description = "Die freigegebenen Karten, nach Nummer.")
  @ApiResponse(
      responseCode = "403",
      description = LESEN_VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = PROJEKT_UNBEKANNT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/projects/{projectId}/night-runs/tonight")
  List<TonightCardView> tonight(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_PROJEKT_ID, example = "7") @PathVariable
          long projectId) {
    return runs.heuteNacht(userId, projectId).stream().map(TonightCardView::of).toList();
  }

  /**
   * Der Fortschritt eines Laufs aus seinen Spuren am Board (Issue #1375, Plan #1372): Ketten,
   * Pakete und ihr Zustand, unbekannte Karten und offene Fragen. 404 für einen Lauf eines anderen
   * Projekts, 404/403 aus der Rechteprüfung wie bei der Laufliste (E10).
   */
  @Operation(
      summary = "Laufstand lesen",
      description =
          "Liefert den Laufstand eines Laufs: wie weit er gekommen ist, abgelesen an seinen"
              + " Spuren am Board — den Karten, die er angelegt oder bewegt hat, und ihren"
              + " Kommentaren. Bei einer Kette (ein Lauf, der eine fachliche Anforderung über"
              + " Plan, Review, Arbeitspakete und Abdeckung führt) stehen unter ketten je"
              + " Anforderung ihr Plan, ihre Pakete und der Weg durch die Stufen (PLAN, REVIEW,"
              + " PAKETE, ABDECKUNG; setzt die Kette ihre Pakete auch um, zusätzlich UMSETZUNG)"
              + " mit der aktuellen Stufe."
              + " pakete nennt alle Arbeitspakete des Laufs mit ihrem Zustand, offeneFragen die"
              + " Karten, die auf eine Antwort eines Menschen warten, unbekannt die Karten, die"
              + " sich keinem Lauf sicher zuordnen lassen. Je Karte kommen nur Nummer, Titel und"
              + " Board, nie Beschreibung oder Kommentare. "
              + LESERECHT)
  @ApiResponse(responseCode = "200", description = "Der Laufstand.")
  @ApiResponse(
      responseCode = "403",
      description = LESEN_VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description =
          "Das Projekt gibt es nicht, der Aufrufer ist dort kein Mitglied, oder der Lauf gehört"
              + " nicht zu diesem Projekt.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/projects/{projectId}/night-runs/{runId}/progress")
  NightRunProgressView progress(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_PROJEKT_ID, example = "7") @PathVariable long projectId,
      @Parameter(description = "Kennung des Laufs, wie sie die Laufliste liefert.", example = "812")
          @PathVariable
          long runId) {
    return NightRunProgressView.of(fortschritt.progress(userId, projectId, runId));
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
        // Fest true und kein Request-Feld: Der Browser liefert einen unabgeschlossenen Lauf
        // ohnehin nicht ein, und ein Feld, das nur einen Wert annehmen kann, taeuschte eine Wahl
        // vor, die es nicht gibt.
        true,
        NightRunUsageRequest.toDomain(request.usage()),
        // Kein Request-Feld aus demselben Grund wie oben: Ein hochgeladenes Protokoll kommt aus
        // einer Datei, nicht aus dem Runner, und traegt dessen Begruendung nicht. Den Rueckfalltext
        // setzt der Dienst (Issue #1068, Plan #1067, E4).
        null,
        // Fest „nicht angegeben" und kein Request-Feld, aus demselben Grund (Issue #1113, Plan
        // #1110 E14): Die Ergebnisdatei verlaesst den Browser nicht (Plan #718, A1), und ein Lauf,
        // den der Server schon fuehrt, darf durch ein zusaetzliches Einlesen nichts verlieren.
        null,
        // Der Abbruchgrund, ebenfalls fest null und aus demselben Grund (Issue #1142, Plan #1139
        // E7): Der Upload-Weg kennt kein Feld dafuer.
        null,
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
        request.excerpt(),
        NightRunUsageRequest.toDomain(request.usage()),
        // Wie das Budget am Lauf: Der Upload-Weg fuehrt keine Stufen (Issue #1113, E14).
        List.of());
  }

  /**
   * Eine leere Liste ist eine Fehleingabe und keine leere Erfolgsantwort ({@code @NotEmpty} → 400)
   * — dasselbe Verhalten wie beim Stapel-Anlegen an einer Board-Spalte ({@code
   * boards/{boardId}/cards/batch}). Der Browser sendet bei einem Protokoll aus lauter Probeläufen
   * gar nicht erst.
   */
  @Schema(description = "Die aus einem Textprotokoll gedeuteten Läufe.")
  record SubmitNightRunsRequest(
      @Schema(description = "Die Läufe, mindestens einer, höchstens 100.")
          @NotEmpty
          @Size(max = MAX_RUNS_PER_REQUEST)
          List<@Valid @NotNull NightRunRequest> runs) {}

  /**
   * Ein einzuliefernder Lauf. Die Liste der Arbeitspakete trägt {@code @Valid} an ihren Elementen —
   * ohne das blieben die Feldgrenzen der Pakete ungeprüft. Leer darf sie sein: Ein harter Abbruch
   * hinterlässt einen Lauf ohne Arbeitspaket.
   */
  @Schema(description = "Ein gedeuteter Lauf samt seiner Arbeitspakete.")
  record NightRunRequest(
      @Schema(
              description = "Startzeitpunkt des Laufs; zugleich sein Schlüssel im Projekt.",
              example = "2026-10-05T01:00:00Z")
          @NotNull
          Instant startedAt,
      @Schema(
              description =
                  "Art des Laufs: IMPLEMENTATION setzt Pakete aus Ready um, REVIEW begutachtet"
                      + " Backlog-Kandidaten, CHAIN führt eine Kette, INTERACTIVE ist eine"
                      + " Sitzung am Rechner.",
              example = "IMPLEMENTATION")
          @NotNull
          NightRunMode mode,
      @Schema(description = "Dauer des Laufs in Millisekunden.", example = "3600000")
          long durationMs,
      @Schema(description = "Zahl der bearbeiteten Arbeitspakete.", example = "3")
          int processedCount,
      @Schema(description = "Zahl der übergangenen Arbeitspakete.", example = "1") int skippedCount,
      @Schema(
              description = "Zahl der Protokollzeilen, die sich nicht deuten ließen.",
              example = "2")
          int unparsedCount,
      @Schema(
              description = "Beispiel ungedeuteter Zeilen; höchstens 4000 Zeichen.",
              example = "unbekannte Zeile: …")
          @Nullable
          @Size(max = NightRunLimits.EXCERPT_MAX)
          String unparsedSample,
      @Schema(description = "Verbrauch des ganzen Laufs.") @Nullable NightRunUsageRequest usage,
      @Schema(description = "Die Arbeitspakete des Laufs, höchstens 200; leer bei hartem Abbruch.")
          @NotNull
          @Size(max = MAX_ITEMS_PER_RUN)
          List<@Valid @NotNull NightRunItemRequest> items) {}

  /** Ein einzulieferndes Arbeitspaket. */
  @Schema(description = "Ein Arbeitspaket des Laufs: eine bearbeitete oder übergangene Karte.")
  record NightRunItemRequest(
      @Schema(description = "Projektweite Nummer der Karte.", example = "1403") int cardNumber,
      @Schema(description = "Titel der Karte, höchstens 300 Zeichen.", example = "Export als CSV")
          @NotBlank
          @Size(max = TITLE_MAX)
          String title,
      @Schema(
              description =
                  "Ergebnis: GREEN abgeschlossen, YELLOW mit Vorbehalt, RED nicht"
                      + " abgeschlossen, GREY übergangen.",
              example = "GREEN")
          @NotNull
          NightRunState state,
      @Schema(description = "Fehlerklasse bei YELLOW oder RED.", example = "CHECKS_RED")
          @Nullable NightRunErrorClass errorClass,
      @Schema(description = "Dauer in Millisekunden.", example = "900000")
          @Nullable Long durationMs,
      @Schema(description = "Commit der Umsetzung, höchstens 40 Zeichen.", example = "b2ae30f6")
          @Nullable
          @Size(max = COMMIT_HASH_MAX)
          String commitHash,
      @Schema(
              description = "Auszug aus der Ausgabe der Sitzung; höchstens 4000 Zeichen.",
              example = "FORTSCHRITT: AK1 — Annotationen gesetzt")
          @Nullable
          @Size(max = NightRunLimits.EXCERPT_MAX)
          String excerpt,
      @Schema(description = "Verbrauch dieses Arbeitspakets.")
          @Nullable NightRunUsageRequest usage) {}

  /** Eine Zeile der Übersicht „Heute Nacht“ (Issue #1454). */
  @Schema(description = "Eine zur Übernahme freigegebene Karte mit ihrem kleinen Stufenstand.")
  record TonightCardView(
      @Schema(description = "Projektweite Nummer der Karte.", example = "1420") int number,
      @Schema(description = "Titel der Karte.", example = "[Fachlich] Export als CSV") String title,
      @Schema(description = "Name des Boards der Karte.", example = "Entwicklung") String boardName,
      @Schema(
              description =
                  "Startstation der Kette: FACHPLAN bei einer fachlichen Anforderung, PLAN bei"
                      + " einem Plan (Plan und Prüfung sind dann vor dem Lauf erbracht).",
              example = "FACHPLAN")
          Startstation start,
      @Schema(
              description =
                  "Zielstation: PLAN, PAKETE, UMSETZUNG oder VORBEREITUNG (Veröffentlichung"
                      + " vorbereitet).",
              example = "PAKETE")
          ProgressStage ziel,
      @Schema(
              description = "Gewählte Prüferzahl (1 oder 2); null ohne Wahl und an einem Plan.",
              example = "2")
          @Nullable Integer pruefer) {

    static TonightCardView of(NachtFreigabe f) {
      return new TonightCardView(
          f.number(), f.title(), f.boardName(), f.start(), f.ziel(), f.pruefer());
    }
  }
}
