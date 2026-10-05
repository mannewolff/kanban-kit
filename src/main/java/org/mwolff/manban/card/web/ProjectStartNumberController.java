package org.mwolff.manban.card.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.mwolff.manban.card.application.ProjectStartNumberService;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Projektweite Startnummer: den effektiven nächsten Wert lesen (Vorbelegung im Editiermodus) und —
 * für Owner/Edit-Berechtigte — neu setzen. Liegt im card-Modul (Nummerierung ist Karten-Belange;
 * project→card wäre ein Modul-Zyklus).
 */
@Tag(name = "Karten")
@RestController
class ProjectStartNumberController {

  private final ProjectStartNumberService service;

  private static final String BESCHREIBUNG_PROJECT_ID = "Interne ID des Projekts.";

  private static final String PROJEKT_FEHLT =
      "Das Projekt gibt es nicht, oder der Aufrufer ist kein Mitglied.";

  ProjectStartNumberController(ProjectStartNumberService service) {
    this.service = service;
  }

  @Operation(
      summary = "Nächste Kartennummer des Projekts lesen",
      description =
          "Liefert die projektweite Nummer, die die nächste angelegte Karte erhält. Verlangt die"
              + " Mitgliedschaft im Projekt.")
  @ApiResponse(responseCode = "200", description = "Die nächste Kartennummer.")
  @ApiResponse(
      responseCode = "404",
      description = PROJEKT_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/projects/{projectId}/next-card-number")
  NextCardNumberView get(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_PROJECT_ID, example = "1") @PathVariable
          long projectId) {
    return new NextCardNumberView(service.effectiveNextCardNumber(userId, projectId));
  }

  @Operation(
      summary = "Nächste Kartennummer des Projekts setzen",
      description =
          "Setzt die projektweite Nummer, ab der neue Karten gezählt werden. Der Wert muss über"
              + " der höchsten bereits vergebenen Nummer liegen und darf die Obergrenze der"
              + " Kartennummern nicht überschreiten.")
  @ApiResponse(responseCode = "200", description = "Die neue nächste Kartennummer.")
  @ApiResponse(
      responseCode = "400",
      description =
          "Der Wert ist kleiner als 1, nicht größer als die höchste vergebene Nummer oder über"
              + " der Obergrenze.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = "Die Projekt-Rolle des Aufrufers umfasst PROJECT_EDIT nicht.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = PROJEKT_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PROJECT_EDIT")
  @PutMapping("/api/projects/{projectId}/next-card-number")
  NextCardNumberView set(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_PROJECT_ID, example = "1") @PathVariable long projectId,
      @Valid @RequestBody NextCardNumberRequest request) {
    return new NextCardNumberView(
        service.setNextCardNumber(userId, projectId, request.nextCardNumber()));
  }

  @Schema(description = "Die gewünschte nächste Kartennummer.")
  record NextCardNumberRequest(
      @Schema(description = "Nächste Kartennummer, mindestens 1.", example = "2000") @Min(1)
          int nextCardNumber) {}

  @Schema(description = "Die nächste Kartennummer des Projekts.")
  record NextCardNumberView(
      @Schema(description = "Nummer, die die nächste angelegte Karte erhält.", example = "1433")
          int nextCardNumber) {}
}
