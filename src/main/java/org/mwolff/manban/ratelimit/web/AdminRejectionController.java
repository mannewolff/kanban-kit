package org.mwolff.manban.ratelimit.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.mwolff.manban.ratelimit.application.OverloadRejectionService;
import org.mwolff.manban.ratelimit.application.OverloadRejectionService.RejectionView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plattform-Admin: Abweisungen wegen Last je Person und Stunde (Issue #1003). Nur per Sitzung
 * erreichbar ({@code SecurityConfig}, {@code /api/admin/**}); die Admin-Prüfung selbst erledigt der
 * {@link OverloadRejectionService}.
 */
@Tag(name = "Plattform-Verwaltung")
@RestController
class AdminRejectionController {

  private final OverloadRejectionService service;

  AdminRejectionController(OverloadRejectionService service) {
    this.service = service;
  }

  @Operation(
      summary = "Abweisungen wegen Last lesen",
      description =
          "Listet, wann und bei wem die Durchsatzbremse Anfragen abgewiesen hat (Antwort 429 an"
              + " die Person), verdichtet je Person und voller Stunde, die jüngste Stunde zuerst."
              + " displayName ist der Anzeigename der Person, #<id>, falls es sie nicht mehr"
              + " gibt; rejections die Zahl der Abweisungen in dieser Stunde.")
  @ApiResponse(responseCode = "200", description = "Die Abweisungen, jüngste Stunde zuerst.")
  @ApiResponse(
      responseCode = "403",
      description = "Der Aufrufer ist kein Plattform-Admin.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PLATTFORM_ADMIN")
  @GetMapping("/api/admin/overload-rejections")
  List<RejectionView> list(@AuthenticationPrincipal Long userId) {
    return service.list(userId);
  }
}
