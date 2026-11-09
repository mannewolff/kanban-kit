package org.mwolff.manban.benachrichtigung.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungEinstellungen;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Der Schalter der Mail-Benachrichtigung je Projekt (Issue #2076, Plan #2070 E12). Nur per Session
 * ({@code ApiZugang}), und nur für echte Owner und Admins des Projekts; die Schreibaufrufe weist
 * die Vorschau „Ansicht als“ ab.
 */
@Tag(name = "Benachrichtigungen")
@RestController
@RequestMapping("/api/projects/{projectId}/benachrichtigung")
class ProjektBenachrichtigungController {

  private static final String KEINE_ROLLE = "Der Aufrufer ist weder Owner noch Admin des Projekts.";

  private static final String KEIN_MITGLIED =
      "Das Projekt gibt es nicht, oder der Aufrufer ist kein echtes Mitglied — auch ein"
          + " Plattform-Admin ohne eigene Mitgliedschaft.";

  private final BenachrichtigungEinstellungen einstellungen;

  ProjektBenachrichtigungController(BenachrichtigungEinstellungen einstellungen) {
    this.einstellungen = einstellungen;
  }

  @Operation(
      summary = "Schalter der Mail-Benachrichtigung eines Projekts lesen",
      description =
          """
          Sagt, ob die Mail-Benachrichtigung für das Projekt eingeschaltet ist. Ohne \
          Einschalten ist sie aus.""")
  @ApiResponse(responseCode = "200", description = "Der Zustand des Schalters.")
  @ApiResponse(
      responseCode = "403",
      description = KEINE_ROLLE,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = KEIN_MITGLIED,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping
  ProjektSchalterView lies(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Kennung des Projekts.", example = "7") @PathVariable
          long projectId) {
    return new ProjektSchalterView(einstellungen.projektschalter(userId, projectId));
  }

  @Operation(
      summary = "Mail-Benachrichtigung eines Projekts ein- oder ausschalten",
      description =
          """
          Schaltet die Mail-Benachrichtigung für das Projekt ein oder aus. Was beim \
          Einschalten schon besteht, wird nicht nachgemeldet; erneutes Einschalten verschiebt \
          diesen Zeitpunkt nicht. Eine Mail geht nur, wenn auch die Person sich eingeschaltet \
          hat.""")
  @ApiResponse(responseCode = "200", description = "Der neue Zustand des Schalters.")
  @ApiResponse(
      responseCode = "400",
      description = "Der Schalter fehlt.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = KEINE_ROLLE,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = KEIN_MITGLIED,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @PutMapping
  ProjektSchalterView schalte(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Kennung des Projekts.", example = "7") @PathVariable long projectId,
      @Valid @RequestBody SchalterRequest request) {
    return new ProjektSchalterView(einstellungen.schalteProjekt(userId, projectId, request.an()));
  }

  /** Ein Schalter, wie ihn die Oberfläche setzt. */
  @Schema(description = "Der gewünschte Zustand eines Schalters.")
  record SchalterRequest(
      @Schema(description = "true schaltet ein, false aus.", example = "true") @NotNull
          Boolean an) {}

  /** Der Zustand des Projektschalters. */
  @Schema(description = "Der Zustand des Schalters eines Projekts.")
  record ProjektSchalterView(
      @Schema(description = "true, wenn die Benachrichtigung eingeschaltet ist.", example = "true")
          boolean an) {}
}
