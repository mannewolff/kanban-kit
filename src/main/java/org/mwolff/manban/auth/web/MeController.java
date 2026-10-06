package org.mwolff.manban.auth.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.mwolff.manban.auth.application.MeService;
import org.mwolff.manban.auth.application.MeService.MeView;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Selbstauskunft und Profilpflege des angemeldeten Benutzers. */
@Tag(
    name = "Profil",
    description =
        "Selbstauskunft und Profil des angemeldeten Benutzers: wer er ist, welche"
            + " Plattform-Rolle er hat und in welchen Projekten er mit welcher Rolle Mitglied ist.")
@RestController
class MeController {

  private final MeService meService;

  MeController(MeService meService) {
    this.meService = meService;
  }

  @Operation(
      summary = "Selbstauskunft lesen",
      description =
          "Liefert das Konto des Aufrufers mit Plattform-Rolle und Projekt-Mitgliedschaften."
              + " Eignet sich auch als Probe, ob eine Anmeldung oder ein Token gültig ist.")
  @ApiResponse(responseCode = "200", description = "Die Selbstauskunft.")
  @GetMapping("/api/me")
  MeView me(@AuthenticationPrincipal Long userId) {
    return meService.load(userId);
  }

  @Operation(
      summary = "Eigenen Anzeigenamen ändern",
      description =
          "Setzt den Anzeigenamen des Aufrufers; führende und folgende Leerzeichen werden"
              + " entfernt. Die Antwort ist die aktualisierte Selbstauskunft.")
  @ApiResponse(responseCode = "200", description = "Die aktualisierte Selbstauskunft.")
  @ApiResponse(
      responseCode = "400",
      description =
          "Ungültige Eingabe: displayName leer oder länger als 120 Zeichen (Details in"
              + " fieldErrors).",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @PatchMapping("/api/me")
  MeView updateProfile(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody UpdateProfileRequest request) {
    return meService.updateDisplayName(userId, request.displayName());
  }

  @Schema(description = "Neuer Anzeigename des Aufrufers.")
  record UpdateProfileRequest(
      @Schema(description = "Anzeigename, höchstens 120 Zeichen.", example = "Ada Lovelace")
          @NotBlank
          @Size(max = 120)
          String displayName) {}
}
