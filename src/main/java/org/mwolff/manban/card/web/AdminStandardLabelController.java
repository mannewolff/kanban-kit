package org.mwolff.manban.card.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.mwolff.manban.card.application.StandardLabelService;
import org.mwolff.manban.card.application.StandardLabelService.Ergebnis;
import org.mwolff.manban.card.application.StandardLabelService.StandardLabel;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plattform-Admin: den Standardsatz der Labels anzeigen und auf allen aktiven Boards anlegen (Issue
 * #1485). Session-Auth erforderlich (siehe {@code SecurityConfig}, {@code /api/admin/**}); die
 * Admin-Autorisierung selbst erledigt der {@link StandardLabelService}.
 */
@Tag(
    name = "Plattform-Verwaltung",
    description =
        "Einstellungen und Verwaltung der ganzen Installation. Alle Aufrufe verlangen einen"
            + " angemeldeten Plattform-Admin (PLATTFORM_ADMIN) und gehen nur mit Session-Cookie,"
            + " nicht mit einem Token.")
@RestController
class AdminStandardLabelController {

  private final StandardLabelService service;

  private static final String KEIN_ADMIN = "Der Aufrufer ist kein Plattform-Admin.";

  AdminStandardLabelController(StandardLabelService service) {
    this.service = service;
  }

  @Operation(
      summary = "Standard-Labels lesen",
      description =
          "Liefert den festen Satz an Labels, die das claude-workflow-kit, die Laufsteuerung und"
              + " die Stufenleiste auf jedem Board erwarten, mit Gruppe und Farbe.")
  @ApiResponse(responseCode = "200", description = "Der Standardsatz in fester Reihenfolge.")
  @ApiResponse(
      responseCode = "403",
      description = KEIN_ADMIN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PLATTFORM_ADMIN")
  @GetMapping("/api/admin/standard-labels")
  List<StandardLabelView> list(@AuthenticationPrincipal Long userId) {
    return service.standardsatz(userId).stream().map(AdminStandardLabelController::view).toList();
  }

  @Operation(
      summary = "Standard-Labels auf allen Boards anlegen",
      description =
          "Legt jedes Label des Standardsatzes auf jedem nicht archivierten Board aller Projekte"
              + " an, dem es fehlt. Ein vorhandenes Label bleibt mit Farbe und Zuordnungen"
              + " unverändert und zählt als übersprungen. Der Aufruf ist idempotent: Jeder"
              + " weitere Aufruf legt nichts mehr an.")
  @ApiResponse(
      responseCode = "200",
      description = "Zahl der Boards, der neu angelegten und der übersprungenen Labels.")
  @ApiResponse(
      responseCode = "403",
      description = KEIN_ADMIN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PLATTFORM_ADMIN")
  @PostMapping("/api/admin/standard-labels/anlegen")
  ErgebnisView anlegen(@AuthenticationPrincipal Long userId) {
    Ergebnis e = service.aufAlleBoardsAnlegen(userId);
    return new ErgebnisView(e.boards(), e.angelegt(), e.uebersprungen());
  }

  private static StandardLabelView view(StandardLabel l) {
    return new StandardLabelView(l.name(), l.gruppe(), l.farbe());
  }

  @Schema(description = "Ein Label des Standardsatzes.")
  record StandardLabelView(
      @Schema(description = "Name des Labels.", example = "kit:night") String name,
      @Schema(description = "Gruppe: Kit, Lauf, Review oder Stufenleiste.", example = "Kit")
          String gruppe,
      @Schema(description = "Farbe, mit der es neu angelegt wird.", example = "#6a1b9a")
          String farbe) {}

  @Schema(description = "Ergebnis des Anlegens der Standard-Labels.")
  record ErgebnisView(
      @Schema(description = "Zahl der nicht archivierten Boards aller Projekte.", example = "4")
          int boards,
      @Schema(description = "Zahl der neu angelegten Labels über alle Boards.", example = "17")
          int angelegt,
      @Schema(description = "Zahl der Labels, die ein Board schon trug.", example = "63")
          int uebersprungen) {}
}
