package org.mwolff.manban.auth.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.mwolff.manban.auth.application.AdminService.UserView;
import org.mwolff.manban.auth.application.BootstrapService;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-Bootstrap: hebt den eingeloggten Nutzer per Env-Token zum ersten Plattform-Admin.
 * Session-Auth erforderlich (über SecurityConfig /api/admin/**), aber KEIN Admin-Recht — sonst
 * könnte niemand je der erste Admin werden.
 */
@Tag(name = "Plattform-Verwaltung")
@RestController
class BootstrapController {

  private final BootstrapService bootstrapService;

  BootstrapController(BootstrapService bootstrapService) {
    this.bootstrapService = bootstrapService;
  }

  @Operation(
      summary = "Ersten Plattform-Admin einrichten",
      description =
          "Hebt den angemeldeten Aufrufer auf einer frischen Installation zum ersten"
              + " Plattform-Admin und gibt sein Konto zugleich frei. Verlangt eine Anmeldung per"
              + " Session-Cookie, aber — anders als die übrige Plattform-Verwaltung — kein"
              + " Admin-Recht, denn es gibt noch keinen Admin. Dafür muss der Aufrufer das"
              + " Bootstrap-Token kennen, das der Betreiber beim Start der Installation"
              + " konfiguriert hat. Möglich nur, solange es keinen Plattform-Admin gibt.")
  @ApiResponse(
      responseCode = "200",
      description = "Das Konto des Aufrufers, jetzt Plattform-Admin.")
  @ApiResponse(
      responseCode = "400",
      description = "Ungültige Eingabe: token leer (Details in fieldErrors).",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description =
          "Das Bootstrap-Token ist falsch, oder auf der Installation ist keines konfiguriert.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description = "Es gibt schon einen Plattform-Admin; der Bootstrap ist nicht mehr möglich.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @PostMapping("/api/admin/bootstrap")
  UserView bootstrap(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody BootstrapRequest request) {
    return bootstrapService.bootstrap(userId, request.token());
  }

  @Schema(description = "Das Bootstrap-Token der Installation.")
  record BootstrapRequest(
      @Schema(
              description =
                  "Das vom Betreiber konfigurierte Bootstrap-Token (Umgebungsvariable der"
                      + " Installation).",
              format = "password")
          @NotBlank
          String token) {}
}
