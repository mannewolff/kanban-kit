package org.mwolff.manban.deployment.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.deployment.application.DeploymentService;
import org.mwolff.manban.deployment.application.GemeldeterStand;
import org.mwolff.manban.deployment.application.ZuletztGemeldet;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die jüngste Vorschau und Veröffentlichung eines Projekts an HTTP (Issue #2215, Plan #2206 E10,
 * fachlich #2202 Kriterium 4).
 *
 * <p>Ein Aufruf des Leitstands mit Sitzung, darum ohne {@code @ApiVertrag}. Keine eigenen
 * Exceptions: 404 liefert der Dienst.
 */
@Tag(name = "Auslieferungen")
@RestController
class AuslieferungZuletztController {

  private final DeploymentService service;

  AuslieferungZuletztController(DeploymentService service) {
    this.service = service;
  }

  @Operation(
      summary = "Jüngste Vorschau und Veröffentlichung lesen",
      description =
          "Nennt die jüngste gemeldete Vorschau und die jüngste gemeldete Veröffentlichung des"
              + " Projekts, je mit Adresse, Stand und Zeitpunkt des Ausgangs, gleich welchen"
              + " Ausgangs. Eine Meldung ohne Art zählt als Veröffentlichung. Fehlt eine davon,"
              + " steht dort null. Verlangt die Mitgliedschaft im Projekt.")
  @ApiResponse(responseCode = "200", description = "Jüngste Vorschau und Veröffentlichung.")
  @ApiResponse(
      responseCode = "404",
      description = "Das Projekt gibt es nicht, oder der Aufrufer ist dort kein Mitglied.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/projects/{projectId}/auslieferung/zuletzt")
  ZuletztView zuletzt(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Interne ID des Projekts.", example = "3") @PathVariable
          long projectId) {
    ZuletztGemeldet zuletzt = service.zuletzt(userId, projectId);
    return new ZuletztView(stand(zuletzt.vorschau()), stand(zuletzt.veroeffentlichung()));
  }

  private static @Nullable StandView stand(@Nullable GemeldeterStand stand) {
    return stand == null ? null : new StandView(stand.url(), stand.revision(), stand.finishedAt());
  }

  /** Jüngste Vorschau und Veröffentlichung. */
  @Schema(description = "Die jüngste Vorschau und die jüngste Veröffentlichung eines Projekts.")
  record ZuletztView(
      @Schema(description = "Die jüngste gemeldete Vorschau; null ohne.")
          @Nullable StandView vorschau,
      @Schema(description = "Die jüngste gemeldete Veröffentlichung; null ohne.")
          @Nullable StandView veroeffentlichung) {}

  /** Ein gemeldeter Stand mit Adresse. */
  @Schema(description = "Ein gemeldeter Stand mit Adresse.")
  record StandView(
      @Schema(
              description =
                  "Adresse der Vorschau bzw. Veröffentlichung; null, wenn keine gemeldet.",
              example = "https://vorschau.example.org/42")
          @Nullable String url,
      @Schema(description = "Der gemeldete Stand (Commit).", example = "3f2c1a9e") String revision,
      @Schema(description = "Zeitpunkt des Ausgangs.", example = "2026-10-17T12:00:00Z")
          Instant finishedAt) {}
}
