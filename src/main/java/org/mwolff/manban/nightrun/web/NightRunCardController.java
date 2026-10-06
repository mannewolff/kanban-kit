package org.mwolff.manban.nightrun.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.nightrun.application.KettenstandDerKarte;
import org.mwolff.manban.nightrun.application.NightRunProgressService;
import org.mwolff.manban.nightrun.application.NightRunService;
import org.mwolff.manban.nightrun.domain.KettenStand;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.mwolff.manban.nightrun.domain.ProgressStage;
import org.mwolff.manban.nightrun.domain.StationStand;
import org.mwolff.manban.nightrun.domain.StationsZustand;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Anläufe einer Karte über Läufe hinweg (Issue #967).
 *
 * <p>Der Endpunkt liegt im Modul {@code nightrun} und nicht in {@code card}: Andersherum zeigte
 * {@code card} auf {@code nightrun}, und die Karte braucht die Läufe für nichts anderes. Die Kante
 * hält {@code ArchitectureTest} fest.
 *
 * <p>Keine eigenen Exceptions: 403 und 404 liefert {@code requireOwner} im {@link NightRunService},
 * 400 eine fehlende oder unlesbare Kartennummer.
 *
 * <p>Dazu der Kettenstand einer Karte für die Stufenleiste (Issue #1452, Plan #1447): Er hängt am
 * Leserecht der Karte, nicht am Lauf-Leserecht.
 */
@Tag(name = "Läufe (Runner)")
@RestController
class NightRunCardController {

  private final NightRunService runs;
  private final NightRunProgressService fortschritt;

  NightRunCardController(NightRunService runs, NightRunProgressService fortschritt) {
    this.runs = runs;
    this.fortschritt = fortschritt;
  }

  /**
   * Jüngster Anlauf zuerst; ein verdrängter Lauf hinterlässt seine Anläufe weiterhin.
   *
   * <p>Beide Gattungen stehen nebeneinander: Der Abruf filtert nicht nach ihr, sondern gibt sie je
   * Anlauf mit aus (Issue #1015, Plan #1007 E10). Eine Karte wird nachts und am Tag angefasst, und
   * beides gehört auf ihr Blatt.
   */
  @Operation(
      summary = "Anläufe einer Karte lesen",
      description =
          "Liefert alle Anläufe an einer Karte über Läufe hinweg, jüngster zuerst. Ein Lauf ist"
              + " eine Sitzung eines Agenten, die Karten abarbeitet; ein Anlauf ist ein Versuch"
              + " eines Laufs an genau dieser Karte, mit Ausgang (state), gegebenenfalls"
              + " Fehlerklasse, Dauer, Commit und Verbrauch. Nachtläufe (kind NIGHT) und"
              + " interaktive Sitzungen (kind INTERACTIVE) stehen nebeneinander. Auch Anläufe"
              + " von Läufen, die die Aufbewahrung schon verdrängt hat, bleiben sichtbar. "
              + NightRunController.LESERECHT)
  @ApiResponse(responseCode = "200", description = "Die Anläufe, jüngster zuerst.")
  @ApiResponse(
      responseCode = "400",
      description = "cardNumber fehlt oder ist keine Zahl.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = NightRunController.LESEN_VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = NightRunController.PROJEKT_UNBEKANNT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/projects/{projectId}/night-runs/items")
  List<NightRunAnlaufView> anlaeufe(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = NightRunController.BESCHREIBUNG_PROJEKT_ID, example = "7")
          @PathVariable
          long projectId,
      @Parameter(description = "Projektweite Nummer der Karte.", example = "1403") @RequestParam
          int cardNumber) {
    return runs.anlaeufeDerKarte(userId, projectId, cardNumber).stream()
        .map(NightRunCardController::view)
        .toList();
  }

  /**
   * Der Stand der Nacht-Kette an einer Karte (Issue #1452, Plan #1447 E5, E14, E15) — Zustand und
   * Text je Station fertig für die Stufenleiste.
   */
  @Operation(
      summary = "Kettenstand einer Karte lesen",
      description =
          "Liefert den Stand der Nacht-Kette, die an dieser Karte startet: Ziel, Prüferzahl, ob das"
              + " Ziel erreicht ist, eine Projektgrenze mit Grund und je Station Zustand, Text und"
              + " Grund. Abgeleitet aus dem Laufstand-Kommentar der Karte; zugrunde liegt der Lauf,"
              + " dessen Kennung der Laufstand trägt, ohne sie der jüngste Anlauf der Karte. Dazu,"
              + " ob ein Runner die Karte übernommen hat, und an einer fachlichen Anforderung, ob"
              + " ihr Plan schon Plan-Review trägt. Lesen darf, wer die Karte lesen darf"
              + " (TICKET_READ).")
  @ApiResponse(responseCode = "200", description = "Der Kettenstand der Karte.")
  @ApiResponse(
      responseCode = "403",
      description = "Der Aufrufer ist Mitglied, seine Rolle umfasst TICKET_READ aber nicht.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description =
          "Die Karte gibt es nicht, oder der Aufrufer ist im Projekt der Karte kein Mitglied.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/cards/{cardId}/night-chain")
  NightChainView kettenstand(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Interne ID der Karte.", example = "812") @PathVariable
          long cardId) {
    return view(fortschritt.kettenstand(userId, cardId));
  }

  private static NightChainView view(KettenstandDerKarte k) {
    KettenStand stand = k.stand();
    KettenStand.Projektgrenze grenze = stand.projektgrenze();
    return new NightChainView(
        stand.ziel(),
        stand.pruefer(),
        stand.zielErreicht(),
        grenze == null ? null : new GrenzeView(grenze.stufe(), grenze.grund()),
        stand.stationen().stream().map(NightRunCardController::view).toList(),
        k.uebernommen(),
        k.planReviewVorhanden(),
        k.lauf());
  }

  private static StationView view(StationStand s) {
    return new StationView(s.station(), s.zustand(), s.text(), s.grund());
  }

  private static NightRunAnlaufView view(NightRunItem item) {
    return new NightRunAnlaufView(
        item.startedAt(),
        item.mode(),
        item.kind(),
        item.state(),
        item.errorClass(),
        item.durationMs(),
        item.commitHash(),
        item.usage());
  }

  /**
   * Ein Anlauf an der Karte.
   *
   * @param startedAt Startzeitpunkt des Laufs
   * @param mode Lauf-Art
   * @param kind Gattung des Anlaufs — Nachtlauf oder interaktive Sitzung (Issue #1015); nie {@code
   *     null}, ein Anlauf ohne eigene Gattung liest sich als {@code NIGHT}
   * @param state Ausgang des Anlaufs
   * @param errorClass Grund eines nicht-grünen Ausgangs
   * @param durationMs Dauer; {@code null} bei übergangenen Paketen
   * @param commitHash Commit der Session; {@code null}, wenn nichts festgeschrieben wurde
   * @param usage die vier Verbrauchszahlen; {@code null}, wenn nichts gemessen wurde — nie 0
   */
  @Schema(description = "Ein Anlauf eines Laufs an der Karte.")
  record NightRunAnlaufView(
      @Schema(description = "Startzeitpunkt des Laufs.", example = "2026-10-05T01:00:00Z")
          Instant startedAt,
      @Schema(
              description =
                  "Art des Laufs: IMPLEMENTATION, REVIEW, CHAIN (Kette) oder INTERACTIVE.",
              example = "IMPLEMENTATION")
          NightRunMode mode,
      @Schema(
              description = "Gattung: NIGHT (Nachtlauf) oder INTERACTIVE (Sitzung am Rechner).",
              example = "NIGHT")
          NightRunKind kind,
      @Schema(
              description =
                  "Ausgang: GREEN abgeschlossen, YELLOW mit Vorbehalt, RED nicht"
                      + " abgeschlossen, GREY übergangen.",
              example = "GREEN")
          NightRunState state,
      @Schema(description = "Fehlerklasse bei YELLOW oder RED.", example = "CHECKS_RED")
          @Nullable NightRunErrorClass errorClass,
      @Schema(description = "Dauer in Millisekunden; null bei übergangenen.", example = "900000")
          @Nullable Long durationMs,
      @Schema(
              description = "Commit der Sitzung; null, wenn nichts festgeschrieben wurde.",
              example = "b2ae30f6")
          @Nullable String commitHash,
      @Schema(description = "Verbrauch; null, wenn nichts gemessen wurde — nie 0.")
          @Nullable NightRunUsage usage) {}

  /** Der Kettenstand einer Karte (Issue #1452). */
  @Schema(description = "Der Stand der Nacht-Kette an einer Karte.")
  record NightChainView(
      @Schema(
              description =
                  "Gewähltes Ziel: PLAN, PAKETE, UMSETZUNG oder VORBEREITUNG; null ohne Ziel —"
                      + " dann endet die Kette nach der Abdeckung.",
              example = "UMSETZUNG")
          @Nullable ProgressStage ziel,
      @Schema(description = "Gewählte Prüferzahl (1 oder 2); null ohne Wahl.", example = "2")
          @Nullable Integer pruefer,
      @Schema(description = "Ob die Kette ihr Ziel erreicht hat.", example = "false")
          boolean zielErreicht,
      @Schema(description = "Die Projektgrenze vor dem Ziel; null ohne Grenze.")
          @Nullable GrenzeView grenze,
      @Schema(description = "Alle Stationen in ihrer Reihenfolge.") List<StationView> stationen,
      @Schema(description = "Ob ein Runner die Karte übernommen hat.", example = "true")
          boolean uebernommen,
      @Schema(
              description =
                  "Ob der Plan dieser fachlichen Anforderung schon Plan-Review trägt; an jeder"
                      + " anderen Karte false.",
              example = "false")
          boolean planReviewVorhanden,
      @Schema(
              description =
                  "Kennung (Start) des zugrunde gelegten Laufs; null, solange keiner die Karte"
                      + " angefasst hat.",
              example = "2026-10-05T01:00:00Z")
          @Nullable Instant lauf) {}

  /** Die Grenze, die das Projekt der Kette setzt. */
  @Schema(description = "Die Grenze, die das Projekt der Kette setzt.")
  record GrenzeView(
      @Schema(description = "Letzte im Projekt erreichbare Stufe.", example = "PAKETE")
          ProgressStage stufe,
      @Schema(
              description =
                  "Der vom Projekt gemeldete Grund, wörtlich; null, solange keiner steht.",
              example = "wartet: Übergang pakete→umsetzung im Projekt nicht freigegeben")
          @Nullable String grund) {}

  /** Eine Station der Stufenleiste. */
  @Schema(description = "Eine Station der Stufenleiste mit Zustand und Text.")
  record StationView(
      @Schema(
              description =
                  "Station: PLAN, REVIEW, PAKETE, ABDECKUNG, UMSETZUNG oder VORBEREITUNG.",
              example = "REVIEW")
          ProgressStage station,
      @Schema(
              description =
                  "Zustand: ERLEDIGT, LAEUFT, WARTET, ABGEBROCHEN, STEHT_AUS, NICHT_VORGESEHEN"
                      + " oder VOR_DEM_LAUF_ERBRACHT.",
              example = "LAEUFT")
          StationsZustand zustand,
      @Schema(description = "Angezeigter Text.", example = "läuft (2 Prüfer)") String text,
      @Schema(
              description = "Grund bei WARTET und ABGEBROCHEN, wörtlich; sonst null.",
              example = "wartet: Frage an den Menschen")
          @Nullable String grund) {}
}
