package org.mwolff.manban.card.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.DoneRetentionSettingService;
import org.mwolff.manban.card.application.DoneRetentionSettingService.RetentionSettings;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plattform-Admin: die globale Done-Aufbewahrung anzeigen und ändern. Session-Auth erforderlich
 * (siehe {@code SecurityConfig}, {@code /api/admin/**}); die Admin-Autorisierung selbst erledigt
 * der {@link DoneRetentionSettingService}.
 */
@Tag(
    name = "Plattform-Verwaltung",
    description =
        "Einstellungen und Verwaltung der ganzen Installation. Alle Aufrufe verlangen einen"
            + " angemeldeten Plattform-Admin (PLATTFORM_ADMIN) und gehen nur mit Session-Cookie,"
            + " nicht mit einem Token.")
@RestController
class AdminRetentionController {

  private final DoneRetentionSettingService service;

  private static final String KEIN_ADMIN = "Der Aufrufer ist kein Plattform-Admin.";

  AdminRetentionController(DoneRetentionSettingService service) {
    this.service = service;
  }

  @Operation(
      summary = "Done-Aufbewahrung lesen",
      description =
          "Liefert die Done-Aufbewahrung der Installation: nach wie vielen Tagen in der"
              + " Done-Spalte eine Karte automatisch archiviert wird. effective ist der wirksame"
              + " Wert, override der hier gesetzte — null, solange der Vorgabewert des Betriebs"
              + " gilt.")
  @ApiResponse(responseCode = "200", description = "Wirksamer und gesetzter Wert.")
  @ApiResponse(
      responseCode = "403",
      description = KEIN_ADMIN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PLATTFORM_ADMIN")
  @GetMapping("/api/admin/done-retention")
  RetentionView get(@AuthenticationPrincipal Long userId) {
    return toView(service.currentFor(userId));
  }

  @Operation(
      summary = "Done-Aufbewahrung setzen",
      description =
          "Setzt die Done-Aufbewahrung der Installation in Tagen; 0 schaltet das automatische"
              + " Archivieren ab. Der Wert gilt für alle Projekte und überschreibt den"
              + " Vorgabewert des Betriebs.")
  @ApiResponse(responseCode = "200", description = "Wirksamer und gesetzter Wert.")
  @ApiResponse(
      responseCode = "400",
      description = "days fehlt oder ist negativ (Details in fieldErrors).",
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
  @PutMapping("/api/admin/done-retention")
  RetentionView update(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody UpdateRetentionRequest request) {
    return toView(service.updateOverride(userId, request.days()));
  }

  private static RetentionView toView(RetentionSettings s) {
    return new RetentionView(s.effective(), s.override());
  }

  /**
   * {@code 0} = Auto-Archiv aus ist erlaubt; negative Werte lehnt die Bean-Validation mit 400 ab.
   */
  @Schema(description = "Die gewünschte Done-Aufbewahrung.")
  record UpdateRetentionRequest(
      @Schema(
              description = "Tage bis zum automatischen Archivieren; 0 schaltet es ab.",
              example = "14")
          @NotNull
          @Min(0)
          Integer days) {}

  @Schema(description = "Die Done-Aufbewahrung der Installation.")
  record RetentionView(
      @Schema(
              description = "Wirksamer Wert in Tagen; 0 heißt kein automatisches Archivieren.",
              example = "14")
          int effective,
      @Schema(
              description = "Hier gesetzter Wert in Tagen; null, solange der Vorgabewert gilt.",
              example = "14")
          @Nullable Integer override) {}
}
