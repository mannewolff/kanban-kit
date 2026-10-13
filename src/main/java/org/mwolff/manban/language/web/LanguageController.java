package org.mwolff.manban.language.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.mwolff.manban.common.i18n.Sprache;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.mwolff.manban.language.application.SpracheService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Sprache der Installation (Issue #1639, Plan #1638 A2): lesen ohne Anmeldung — auch die Seiten
 * vor der Anmeldung brauchen sie —, setzen nur als Plattform-Admin per Sitzung ({@code
 * /api/admin/**}). Die Admin-Prüfung selbst erledigt der {@link SpracheService}.
 */
@Tag(
    name = "Sprache",
    description =
        "Die Sprache der Oberfläche, für die ganze Installation: Deutsch (de) oder Englisch (en).")
@RestController
class LanguageController {

  private final SpracheService service;

  LanguageController(SpracheService service) {
    this.service = service;
  }

  @Operation(
      summary = "Sprache lesen",
      description =
          "Liefert die Sprache der Installation, de oder en. Ohne Anmeldung lesbar; solange"
              + " keine gesetzt ist, gilt de.")
  @ApiResponse(responseCode = "200", description = "Die geltende Sprache.")
  @GetMapping("/api/language")
  LanguageView lesen() {
    return new LanguageView(service.lesen().code());
  }

  @Operation(
      summary = "Sprache setzen",
      description =
          "Setzt die Sprache der Installation für alle Nutzer und die Seiten vor der Anmeldung."
              + " Sie wirkt ohne Neustart, spätestens beim nächsten Laden der Seite.")
  @ApiResponse(responseCode = "200", description = "Die neu gesetzte Sprache.")
  @ApiResponse(
      responseCode = "400",
      description = "language fehlt oder ist weder de noch en (Details in fieldErrors).",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = "Der Aufrufer ist kein Plattform-Admin.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PLATTFORM_ADMIN")
  @PutMapping("/api/admin/language")
  LanguageView setzen(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody UpdateLanguageRequest request) {
    return new LanguageView(service.setzen(userId, Sprache.ausCode(request.language())).code());
  }

  /** Nur die exakten Codes; alles andere lehnt die Bean-Validation mit 400 ab. */
  @Schema(description = "Die gewünschte Sprache.")
  record UpdateLanguageRequest(
      @Schema(
              description = "Sprachcode.",
              allowableValues = {"de", "en"},
              example = "en")
          @NotNull
          @Pattern(regexp = "de|en")
          String language) {}

  @Schema(description = "Die Sprache der Installation.")
  record LanguageView(
      @Schema(
              description = "Sprachcode.",
              allowableValues = {"de", "en"},
              example = "de")
          String language) {}
}
