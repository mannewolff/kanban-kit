package org.mwolff.manban.nightrun.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.ZoneId;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.mwolff.manban.nightrun.application.DisruptionService;
import org.mwolff.manban.nightrun.application.DisruptionService.LeitstandView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Der Plattform-Leitstand an HTTP (Issue #1080, auf einen Endpunkt für drei Listen umgestellt in
 * #1095).
 *
 * <p><b>Der Pfadstamm {@code /api/admin} ist eine Sicherheitsentscheidung, keine
 * Geschmacksfrage</b> (Plan #1072 E24): {@code SecurityConfig} verlangt für {@code /api/admin/**}
 * ausdrücklich eine Sitzung und lässt kein PAT zu, während die Auffangregel {@code /api/**} auch
 * ein ungebundenes Token durchlässt. Ein Pfad wie {@code /api/platform/...} fiele unter die
 * Auffangregel — die Läufe und Störungen aller Projekte samt Quittieren wären dann mit einem Token
 * erreichbar.
 *
 * <p><b>Ein Abruf für die Ansicht, ein eigener fürs Quittieren</b> (Plan #1088 E5): Die drei Listen
 * kommen in einer Antwort, weil die Seite sich auffrischt und ein Lauf zwischen zwei Rundreisen den
 * Bereich wechseln kann. Das Quittieren bleibt daneben — es ist eine Einzelaktion, keine Ansicht.
 *
 * <p>Keine eigenen Exceptions: 403 und 404 liefert {@link DisruptionService}, 400 die Bindung und
 * die Abweisung der Zone über {@link Regionszone}.
 */
@Tag(name = "Plattform-Verwaltung")
@RestController
@RequestMapping("/api/admin")
class DisruptionController {

  private static final String KEIN_ADMIN = "Der Aufrufer ist kein Plattform-Admin.";

  private static final String BESCHREIBUNG_LAUF_ID =
      "Kennung des Laufs, wie sie die Ansicht des Plattform-Leitstands liefert.";

  private static final String LAUF_UNBEKANNT =
      "Den Lauf gibt es nicht (mehr) — auch, weil ihn die Aufbewahrung verdrängt hat —, oder sein"
          + " Projekt nimmt nicht am Plattform-Leitstand teil.";

  private final DisruptionService disruptions;

  DisruptionController(DisruptionService disruptions) {
    this.disruptions = disruptions;
  }

  /**
   * Die drei Listen der Ansicht. Die Zone kommt vom Browser (Plan #1088 E6) — im Container läuft
   * die JVM regelmäßig in UTC, und die Nachtgrenze läge dann um Stunden verschoben.
   */
  @Operation(
      summary = "Plattform-Leitstand lesen",
      description =
          "Liefert die Läufe aller Projekte, die am Plattform-Leitstand teilnehmen, in einer"
              + " Antwort. Ein Lauf ist eine Sitzung eines Agenten, die Karten abarbeitet — ein"
              + " Nachtlauf des Runners oder eine interaktive Sitzung. Die Listen: laufende"
              + " (Läufe, die noch arbeiten, gleich wann sie begannen; dazu gemeldetePakete mit"
              + " den bisher gemeldeten Arbeitspaketen je laufendem Lauf), durchgefuehrte"
              + " (beendete Läufe des laufenden Zyklus, verstummte eingeschlossen),"
              + " durchgefuehrteVoriger (dieselben des vorigen Zyklus) und stoerungen (offene,"
              + " noch nicht quittierte Störungen über alle Nächte). Ein Zyklus reicht von 12:00"
              + " bis 12:00 in der übergebenen Zone, so dass eine Nacht in einem Zyklus liegt."
              + " Ein Lauf gilt als verstummt, wenn er sich eine Frist lang nicht mehr gemeldet"
              + " hat, ohne abgeschlossen zu sein.")
  @ApiResponse(responseCode = "200", description = "Die Listen des Plattform-Leitstands.")
  @ApiResponse(
      responseCode = "400",
      description =
          "zone fehlt, ist unbekannt oder ist ein fester Versatz statt einer Regionszone.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = KEIN_ADMIN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PLATTFORM_ADMIN")
  @GetMapping("/leitstand")
  LeitstandView leitstand(
      @AuthenticationPrincipal Long userId,
      @Parameter(
              description =
                  "Regionszone des Lesers, in der die Zyklusgrenze um 12:00 gezogen wird; ein"
                      + " fester Versatz wie +02:00 wird abgewiesen.",
              example = "Europe/Berlin")
          @RequestParam
          ZoneId zone) {
    return disruptions.leitstand(userId, Regionszone.of(zone));
  }

  /** Quittieren heißt „gesehen" — ohne Rückfrage, ohne Rückgängig, idempotent (AK 8). */
  @Operation(
      summary = "Störung quittieren",
      description =
          "Quittiert die Störung eines Laufs: „gesehen“. Die Störung verschwindet aus der Liste"
              + " stoerungen; der Lauf selbst bleibt unverändert. Eine Störung ist ein Lauf, der"
              + " gescheitert ist, seinen Abbruch gemeldet hat oder verstummt ist, oder dessen"
              + " Paket auf einen Menschen wartet. Ohne Rückgängig und idempotent — ein zweiter"
              + " Aufruf für dieselbe Störung ist kein Fehler.")
  @ApiResponse(responseCode = "204", description = "Die Störung ist quittiert.")
  @ApiResponse(
      responseCode = "403",
      description = KEIN_ADMIN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = LAUF_UNBEKANNT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PLATTFORM_ADMIN")
  @DeleteMapping("/disruptions/{laufId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void acknowledge(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_LAUF_ID, example = "812") @PathVariable long laufId) {
    disruptions.acknowledge(userId, laufId);
  }

  /**
   * Kennzeichnet einen hängenden Lauf von Hand als beendet (Issue #1197).
   *
   * <p><b>{@code POST} und nicht {@code DELETE}</b> wie das Quittieren daneben: Gelöscht wird
   * nichts — der Lauf bleibt mitsamt seinen Paketen stehen und bekommt einen Vermerk. Der eigene
   * Pfadstamm {@code /night-runs} sagt dasselbe: Dies ist eine Aussage über den <em>Lauf</em>,
   * während die Quittung eine über die Sichtung einer Störung ist.
   *
   * <p>404, 409 und 403 liefert {@link DisruptionService}.
   */
  @Operation(
      summary = "Hängenden Lauf als beendet kennzeichnen",
      description =
          "Kennzeichnet einen Lauf von Hand als beendet, dessen Prozess nicht mehr existiert und"
              + " der sich darum nie mehr abmeldet. Der Lauf bleibt samt seinen Arbeitspaketen"
              + " stehen und bekommt einen Vermerk, wer ihn wann gekennzeichnet hat; gelöscht wird"
              + " nichts. Nur ein laufender Lauf lässt sich kennzeichnen. Idempotent: Ein schon"
              + " gekennzeichneter Lauf ist kein Fehler, der erste Vermerk bleibt.")
  @ApiResponse(responseCode = "204", description = "Der Lauf ist als beendet gekennzeichnet.")
  @ApiResponse(
      responseCode = "403",
      description = KEIN_ADMIN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = LAUF_UNBEKANNT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description =
          "Der Lauf läuft nicht mehr: Er hat einen Ausgang gemeldet oder ist verstummt. Eine"
              + " Störung wird quittiert, nicht gekennzeichnet.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PLATTFORM_ADMIN")
  @PostMapping("/night-runs/{laufId}/close")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void close(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_LAUF_ID, example = "812") @PathVariable long laufId) {
    disruptions.close(userId, laufId);
  }
}
