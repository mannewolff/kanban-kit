package org.mwolff.manban.nightrun.web;

import static org.mwolff.manban.nightrun.web.NightRunController.ABORT_REASON_MAX;
import static org.mwolff.manban.nightrun.web.NightRunController.COMMIT_HASH_MAX;
import static org.mwolff.manban.nightrun.web.NightRunController.MAX_ITEMS_PER_RUN;
import static org.mwolff.manban.nightrun.web.NightRunController.NO_WORK_REASON_MAX;
import static org.mwolff.manban.nightrun.web.NightRunController.TITLE_MAX;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.mwolff.manban.common.web.api.Stabilitaet;
import org.mwolff.manban.nightrun.application.NightRunService;
import org.mwolff.manban.nightrun.application.NightRunService.NewNightRun;
import org.mwolff.manban.nightrun.application.NightRunService.NewNightRunItem;
import org.mwolff.manban.nightrun.application.NightRunService.NewReleasePreparation;
import org.mwolff.manban.nightrun.application.NightRunService.NightRunResult;
import org.mwolff.manban.nightrun.application.TokenNotBoundForIngestException;
import org.mwolff.manban.nightrun.domain.NightRunAbortKind;
import org.mwolff.manban.nightrun.domain.NightRunBudget;
import org.mwolff.manban.nightrun.domain.NightRunBudgetOrigin;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItemStage;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunLimits;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunStage;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.ReleasePreparationResult;
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
 *
 * <p>Die API-Beschreibung (Issue #1403, Plan #1400) steht an Methode und Records; der Aufruf ist
 * verlässlich (E4), weil das Kit ihn benutzt.
 */
// Die OpenAPI-Annotationen heben die Importzahl über die PMD-Schwelle von 40 (Plan #1400, E14).
// Die Ausnahme bleibt an dieser Klasse sichtbar, statt die Regel für alle anzuheben.
@SuppressWarnings("PMD.ExcessiveImports")
@Tag(
    name = "Läufe (Runner)",
    description =
        """
        Läufe von Agenten an den Leitstand melden und auswerten. Ein Lauf ist eine Sitzung eines \
        Agenten, die Karten abarbeitet — ein Nachtlauf des Runners oder eine interaktive \
        Sitzung am Rechner eines Menschen. Eine Kette ist ein Lauf, der eine Anforderung \
        über die Stufen PLAN, REVIEW, PAKETE und ABDECKUNG bis zur Umsetzung führt. Die \
        Runner-Seite zeigt die gemeldeten Läufe.""")
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

  /**
   * Längengrenze der Versionsbeschriftung der Morgenmeldung (Issue #1456) — die Länge der Spalte
   * {@code night_run_release_preparation.version} aus {@code V48}. Ohne Grenze risse eine überlange
   * Meldung dort in einen Serverfehler statt in eine benannte Ablehnung.
   */
  static final int RELEASE_VERSION_MAX = 100;

  /**
   * Längengrenze der fehlgeschlagenen Prüfung und eines offenen Eintrags der Morgenmeldung — die
   * Länge der Spalten {@code red_check} und {@code night_run_release_entry.text} aus {@code V48}.
   */
  static final int RELEASE_TEXT_MAX = 300;

  /**
   * Obergrenze der Kartennummern je Liste der Morgenmeldung. Ein vorbereiteter Stand kann Pakete
   * aus mehreren Ketten enthalten (fachlich #1420), darum mehr als die {@value
   * NightRunController#MAX_ITEMS_PER_RUN} Vorgänge eines Laufs.
   */
  static final int RELEASE_CARDS_MAX = 500;

  /** Obergrenze der offenen Einträge der Morgenmeldung. */
  static final int RELEASE_PENDING_MAX = 50;

  /**
   * Obergrenzen der Dateiliste der Morgenmeldung. Sie wird angenommen, aber nicht gespeichert (Plan
   * #1447 E12); begrenzt ist sie trotzdem, damit eine Meldung nicht beliebig groß werden kann.
   */
  static final int RELEASE_FILES_MAX = 1000;

  /** Längengrenze eines Eintrags der Dateiliste; siehe {@link #RELEASE_FILES_MAX}. */
  static final int RELEASE_FILE_MAX = 500;

  private final NightRunService service;

  NightRunIngestController(NightRunService service) {
    this.service = service;
  }

  @Operation(
      summary = "Lauf einliefern",
      description =
          """
          Meldet den vollständigen Stand eines Laufs mit einem projektgebundenen Projekt-Token \
          (Header X-Kanban-Token). Das Zielprojekt kommt aus der Bindung des Tokens, nicht \
          aus dem Aufruf; das Token darf nur, was die Person darf, die es angelegt hat — \
          hier verlangt das die Rolle Owner im Projekt.\n\n\
          Ein Lauf je Aufruf. Schlüssel ist startedAt: Die erste Meldung legt den Lauf an, \
          jede weitere mit demselben startedAt ersetzt ihn vollständig (Zustand, keine \
          Ergänzung). Ein Lauf meldet sich so im Verlauf mehrfach.\n\n\
          kind unterscheidet den Nachtlauf (NIGHT, die Vorgabe) von der interaktiven \
          Sitzung (INTERACTIVE). Bei einer Kette (mode CHAIN) tragen die Vorgänge ihre \
          Stufen (stages) mit Dauer und Verbrauch; budget nennt die Zeit- und \
          Kostenvorgaben, unter denen der Lauf antrat.\n\n\
          releasePreparation ist die Morgenmeldung: ob und wie der Lauf eine \
          Veröffentlichung vorbereitet hat. Sie ersetzt wie jedes andere Feld eine früher \
          gemeldete; fehlt sie, steht am Lauf keine. Den Eingang setzt der Server.\n\n\
          Gegenstück auf der Runner-Seite ist „Protokoll einlesen“: Dort lädt ein Mensch \
          das Textprotokoll eines Laufs im Browser hoch, und der Leitstand deutet es \
          zeilenweise. Dieser Aufruf nimmt denselben Inhalt strukturiert an, ohne \
          Textprotokoll und ohne ungedeutete Zeilen.""")
  @ApiResponse(
      responseCode = "200",
      description = "Der Lauf ist angelegt (CREATED) oder ersetzt (REPLACED).")
  @ApiResponse(
      responseCode = "400",
      description =
          """
          Ungültige Eingabe: ein Feld fehlt oder verletzt seine Grenzen (Details in \
          fieldErrors). Ebenso: das Projekt-Token ist an kein Projekt gebunden.""",
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
  @ApiVertrag(stabilitaet = Stabilitaet.VERLAESSLICH)
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
        request.abortReason(),
        request.abortKind(),
        vorbereitung(request.releasePreparation()),
        request.items().stream().map(NightRunIngestController::item).toList());
  }

  /**
   * Die gemeldete Morgenmeldung als Wert des Dienstes — oder {@code null}, wenn keine gemeldet
   * wurde (Issue #1456). Fehlende Listen werden zur leeren Liste: „keine offenen Prüfungen" ist
   * eine Aussage. {@code releaseFiles} wird hier verworfen (Plan #1447 E12).
   */
  private static @Nullable NewReleasePreparation vorbereitung(
      @Nullable IngestReleasePreparationRequest request) {
    if (request == null) {
      return null;
    }
    return new NewReleasePreparation(
        request.result(),
        request.commitHash(),
        request.version(),
        request.redCheck(),
        leerStattNull(request.cardNumbers()),
        leerStattNull(request.redCards()),
        leerStattNull(request.pending()));
  }

  private static <T> List<T> leerStattNull(@Nullable List<T> liste) {
    return liste == null ? List.of() : liste;
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
  @Schema(description = "CREATED: neu angelegt; REPLACED: ein vorhandener Lauf ersetzt.")
  enum Outcome {
    CREATED,
    REPLACED
  }

  /** Antwort: der fachliche Schlüssel des Laufs und was mit ihm geschah. */
  @Schema(description = "Ergebnis einer Einlieferung.")
  record IngestResponse(
      @Schema(
              description = "Schlüssel des Laufs: sein Startzeitpunkt.",
              example = "2026-10-05T01:00:00Z")
          Instant startedAt,
      @Schema(description = "Was mit dem Lauf geschah.", example = "CREATED") Outcome outcome) {}

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
   * @param abortReason Grund, warum der Lauf hart abgebrochen ist (Issue #1142). Additiv und
   *     {@code @Nullable} aus demselben Grund wie die drei davor: Eine ältere Kit-Kopie kennt das
   *     Feld nicht und meldet unverändert weiter. Begrenzt auf {@link
   *     NightRunController#ABORT_REASON_MAX} — die Länge der Spalte, in die der Wert geht; ohne
   *     Grenze risse eine überlange Meldung dort in einen Serverfehler statt in eine benannte
   *     Ablehnung. Ob der Wert am Lauf landet, entscheidet der Dienst.
   * @param abortKind wie der abgebrochene Lauf zu seinem Abschluss kam (Issue #1500, Plan #1498
   *     E2). Additiv und {@code @Nullable} aus demselben Grund wie {@code abortReason}: Eine ältere
   *     Kit-Kopie kennt das Feld nicht und meldet unverändert weiter; ihr Abbruch bleibt dann wie
   *     bisher eine Störung (A4). Ein unbekannter Wert wird abgewiesen. Ob der Wert am Lauf landet,
   *     entscheidet der Dienst — nur zusammen mit dem Abbruchgrund (E4).
   * @param releasePreparation die Morgenmeldung des Laufs (Issue #1456, Plan #1447 E12). Additiv
   *     und {@code @Nullable} aus demselben Grund wie die vier davor: Eine ältere Kit-Kopie kennt
   *     das Feld nicht und meldet unverändert weiter.
   */
  @Schema(description = "Der vollständige Stand eines Laufs; ersetzt eine frühere Meldung.")
  record IngestRequest(
      @Schema(
              description =
                  """
                  Startzeitpunkt des Laufs; zugleich sein Schlüssel im Projekt. Dieselbe \
                  Kennung trägt der Header X-Night-Run der Kommentare des Laufs.""",
              example = "2026-10-05T01:00:00Z")
          @NotNull
          Instant startedAt,
      @Schema(
              description =
                  """
                  Art des Laufs: IMPLEMENTATION setzt Pakete aus Ready um, REVIEW begutachtet \
                  Backlog-Kandidaten, CHAIN führt eine Kette, INTERACTIVE ist eine \
                  Sitzung am Rechner.""",
              example = "IMPLEMENTATION")
          @NotNull
          NightRunMode mode,
      @Schema(
              description = "Gattung: NIGHT (Vorgabe, wenn das Feld fehlt) oder INTERACTIVE.",
              example = "NIGHT")
          @Nullable NightRunKind kind,
      @Schema(description = "Dauer des Laufs in Millisekunden.", example = "3600000")
          long durationMs,
      @Schema(description = "Zahl der bearbeiteten Vorgänge.", example = "3") int processedCount,
      @Schema(description = "Zahl der übergangenen Vorgänge.", example = "1") int skippedCount,
      @Schema(description = "Zahl ungedeuteter Zeilen; hier stets 0.", example = "0")
          int unparsedCount,
      @Schema(
              description = "true, wenn der Lauf beendet ist; false, solange er noch läuft.",
              example = "true")
          @NotNull
          Boolean complete,
      @Schema(description = "Verbrauch des ganzen Laufs, auch der Teil außerhalb der Vorgänge.")
          @Nullable
          @Valid
          NightRunUsageRequest usage,
      @Schema(
              description = "Grund, warum nichts abzuarbeiten war; höchstens 300 Zeichen.",
              example = "Ready ist leer.")
          @Nullable
          @Size(max = NO_WORK_REASON_MAX)
          String noWorkReason,
      @Schema(description = "Vorgaben, unter denen der Lauf antrat.") @Nullable @Valid
          IngestBudgetRequest budget,
      @Schema(
              description = "Grund eines harten Abbruchs; höchstens 4000 Zeichen.",
              example = "Sitzungszeitgrenze erreicht")
          @Nullable
          @Size(max = ABORT_REASON_MAX)
          String abortReason,
      @Schema(
              description =
                  """
                  Wie der abgebrochene Lauf endete: REPORTED, wenn er seinen Abbruch selbst \
                  gemeldet hat; SILENCED, wenn der Wächter ihn nach dem Verstummen \
                  abgeschlossen hat. Gilt nur zusammen mit abortReason.""",
              example = "REPORTED")
          @Nullable NightRunAbortKind abortKind,
      @Schema(
              description =
                  """
                  Morgenmeldung: ob und wie der Lauf eine Veröffentlichung vorbereitet hat. \
                  Fehlt sie, trägt der Lauf keine.""")
          @Nullable
          @Valid
          IngestReleasePreparationRequest releasePreparation,
      @Schema(description = "Die Vorgänge des Laufs, höchstens 200.")
          @NotNull
          @Size(max = MAX_ITEMS_PER_RUN)
          List<@Valid @NotNull IngestItemRequest> items) {}

  /**
   * Ein gemeldetes Arbeitspaket.
   *
   * @param stages die Stufen der Kette, die dieser Vorgang durchlaufen hat (Issue #1113). Additiv
   *     und {@code @Nullable} wie {@code budget}; fehlt das Feld, hatte der Vorgang keine Stufen.
   */
  @Schema(description = "Ein Vorgang des Laufs: eine bearbeitete oder übergangene Karte.")
  record IngestItemRequest(
      @Schema(description = "Projektweite Nummer der Karte.", example = "1403") int cardNumber,
      @Schema(description = "Titel der Karte, höchstens 300 Zeichen.", example = "Export als CSV")
          @NotBlank
          @Size(max = TITLE_MAX)
          String title,
      @Schema(
              description =
                  """
                  Ergebnis: GREEN abgeschlossen, YELLOW mit Vorbehalt, RED nicht \
                  abgeschlossen, GREY übergangen.""",
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
      @Schema(description = "Verbrauch dieses Vorgangs.") @Nullable @Valid
          NightRunUsageRequest usage,
      @Schema(description = "Stufen einer Kette, die der Vorgang durchlief; höchstens vier.")
          @Nullable
          @Size(max = MAX_STAGES_PER_ITEM)
          List<@Valid @NotNull IngestStageRequest> stages) {}

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
  @Schema(description = "Zeit- und Kostenvorgaben eines Kettenlaufs; jedes Feld darf fehlen.")
  record IngestBudgetRequest(
      @Schema(description = "Zeitvorgabe der Stufe PLAN in Minuten.", example = "30")
          @Nullable Integer planMin,
      @Schema(description = "Zeitvorgabe der Stufe REVIEW in Minuten.", example = "20")
          @Nullable Integer reviewMin,
      @Schema(description = "Zeitvorgabe der Stufe PAKETE in Minuten.", example = "120")
          @Nullable Integer paketeMin,
      @Schema(description = "Zeitvorgabe der Stufe ABDECKUNG in Minuten.", example = "20")
          @Nullable Integer abdeckungMin,
      @Schema(description = "Kostenvorgabe in US-Dollar.", example = "25.00")
          @Nullable BigDecimal kostenUsd,
      @Schema(
              description =
                  """
                  CONFIGURED: alle Vorgaben aus der Konfiguration; DEFAULTED: mindestens eine \
                  aus den Voreinstellungen.""",
              example = "CONFIGURED")
          @Nullable NightRunBudgetOrigin origin,
      @Schema(
              description =
                  """
                  Namen der Felder, die aus den Voreinstellungen kamen; höchstens 10 zu je 18 \
                  Zeichen.""",
              example = "[\"planMin\"]")
          @Nullable
          @Size(max = MAX_DEFAULT_FIELDS)
          List<@NotNull @Size(max = DEFAULT_FIELD_NAME_MAX) String> defaultFields) {}

  /**
   * Die gemeldeten Messwerte einer Stufe der Kette (Issue #1113).
   *
   * <p>{@code stage} ist Pflicht und keine Ausnahme von E11: Die Stufe ist die Identität des
   * Eintrags, und ein Eintrag ohne sie sagt nichts — er ließe sich weder anzeigen noch einer
   * Zeitvorgabe zuordnen.
   */
  @Schema(description = "Eine Stufe der Kette, die ein Vorgang durchlief.")
  record IngestStageRequest(
      @Schema(description = "Die Stufe: PLAN, REVIEW, PAKETE oder ABDECKUNG.", example = "PAKETE")
          @NotNull
          NightRunStage stage,
      @Schema(description = "Dauer der Stufe in Millisekunden.", example = "600000")
          @Nullable Long durationMs,
      @Schema(description = "Verbrauch der Stufe.") @Nullable @Valid NightRunUsageRequest usage) {}

  /**
   * Die gemeldete Morgenmeldung eines Laufs (Issue #1456, Plan #1447 E12; Kit A7).
   *
   * <p>{@code result} ist Pflicht und keine Ausnahme von der Additivität: Es ist die Aussage der
   * Meldung, und eine Meldung ohne sie sagt nichts. Alle übrigen Felder dürfen fehlen. Die Grenzen
   * folgen den Spalten aus {@code V48} — ohne sie risse eine überlange Meldung dort in einen
   * Serverfehler statt in eine benannte Ablehnung.
   */
  @Schema(description = "Morgenmeldung: der vorbereitete Stand einer Veröffentlichung.")
  record IngestReleasePreparationRequest(
      @Schema(
              description =
                  """
                  Ausgang: GREEN grün, GREEN_PENDING grün mit offener Prüfung, RED rot, \
                  NOT_PREPARED nichts vorbereitet.""",
              example = "GREEN")
          @NotNull
          ReleasePreparationResult result,
      @Schema(
              description =
                  """
                  Commit des vorbereiteten Stands, höchstens 40 Zeichen; damit findet der Mensch \
                  ihn außerhalb des Boards wieder.""",
              example = "b2ae30f6")
          @Nullable
          @Size(max = COMMIT_HASH_MAX)
          String commitHash,
      @Schema(description = "Beschriftung des Stands, höchstens 100 Zeichen.", example = "1.4.0")
          @Nullable
          @Size(max = RELEASE_VERSION_MAX)
          String version,
      @Schema(
              description =
                  """
                  Dateien der Veröffentlichung; höchstens 1000 zu je 500 Zeichen. Wird angenommen, \
                  aber nicht gespeichert.""",
              example = "[\"target/manban.jar\"]")
          @Nullable
          @Size(max = RELEASE_FILES_MAX)
          List<@NotNull @Size(max = RELEASE_FILE_MAX) String> releaseFiles,
      @Schema(
              description =
                  "Noch offene Prüfungen bei GREEN_PENDING; höchstens 50 zu je 300 Zeichen.",
              example = "[\"Mutationsprüfung Frontend\"]")
          @Nullable
          @Size(max = RELEASE_PENDING_MAX)
          List<@NotBlank @Size(max = RELEASE_TEXT_MAX) String> pending,
      @Schema(
              description = "Nummern der enthaltenen Arbeitspakete; höchstens 500.",
              example = "[1449, 1450]")
          @Nullable
          @Size(max = RELEASE_CARDS_MAX)
          List<@NotNull @Positive Integer> cardNumbers,
      @Schema(
              description = "Die fehlgeschlagene Prüfung bei RED, höchstens 300 Zeichen.",
              example = "mvn verify")
          @Nullable
          @Size(max = RELEASE_TEXT_MAX)
          String redCheck,
      @Schema(
              description = "Nummern der Karten, die die fehlgeschlagene Prüfung betrifft.",
              example = "[1450]")
          @Nullable
          @Size(max = RELEASE_CARDS_MAX)
          List<@NotNull @Positive Integer> redCards) {}
}
