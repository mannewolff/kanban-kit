package org.mwolff.manban.anbieter.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.mwolff.manban.anbieter.application.AnbieterLageService;
import org.mwolff.manban.anbieter.application.AnbieterLageService.AnbieterLage;
import org.mwolff.manban.anbieter.application.WartetAufDichService;
import org.mwolff.manban.anbieter.application.WartetAufDichService.WartendeKarte;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * „Wartet auf dich“ und die Erreichbarkeit des Rechenzentrums an HTTP (Issue #2216, Plan #2206 E12,
 * E14, Fachplan #2202 Kriterien 3 und 10).
 *
 * <p>Aufrufe des Leitstands mit Sitzung, darum ohne {@code @ApiVertrag}. Keine eigenen Exceptions:
 * 404 liefern die Dienste.
 */
@Tag(name = "Anbieter")
@RestController
class AnbieterController {

  private final WartetAufDichService wartende;
  private final AnbieterLageService lagen;

  AnbieterController(WartetAufDichService wartende, AnbieterLageService lagen) {
    this.wartende = wartende;
    this.lagen = lagen;
  }

  @Operation(
      summary = "Karten lesen, die auf den Kunden warten",
      description =
          "Nennt die Karten des Projekts mit einer offenen Rückfrage der KI oder einer"
              + " ausstehenden Freigabe, in der Reihenfolge der Boards. Beantwortete und überholte"
              + " Rückfragen zählen nicht, ebenso Karten, die der Aufrufer nicht sieht. Ein"
              + " Projekt mit fremdem Board liefert eine leere Liste. Verlangt die Mitgliedschaft"
              + " im Projekt.")
  @ApiResponse(responseCode = "200", description = "Die wartenden Karten; leer ohne.")
  @ApiResponse(
      responseCode = "404",
      description = "Das Projekt gibt es nicht, oder der Aufrufer ist dort kein Mitglied.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/projects/{projectId}/wartet-auf-dich")
  List<WartendeKarte> wartetAufDich(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Interne ID des Projekts.", example = "3") @PathVariable
          long projectId) {
    return wartende.liste(userId, projectId);
  }

  @Operation(
      summary = "Erreichbarkeit des Rechenzentrums lesen",
      description =
          "Sagt je Board mit Label der Sache Anbieter, ob das Rechenzentrum die Karten länger als"
              + " die Abholfrist (Vorgabe 6 Minuten) nicht gelesen hat. Vor dem ersten Lesen gibt"
              + " es keinen Hinweis. Den Hinweis bekommt nur die Rolle Kunde; jedes andere"
              + " Mitglied bekommt eine Lage ohne Hinweis. Verlangt die Mitgliedschaft im"
              + " Projekt.")
  @ApiResponse(responseCode = "200", description = "Die Lage des Projekts.")
  @ApiResponse(
      responseCode = "404",
      description = "Das Projekt gibt es nicht, oder der Aufrufer ist dort kein Mitglied.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/projects/{projectId}/anbieter/lage")
  AnbieterLage lage(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Interne ID des Projekts.", example = "3") @PathVariable
          long projectId) {
    return lagen.lage(userId, projectId);
  }
}
