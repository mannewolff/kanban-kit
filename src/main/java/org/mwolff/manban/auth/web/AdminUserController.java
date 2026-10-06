package org.mwolff.manban.auth.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.mwolff.manban.auth.application.AdminService;
import org.mwolff.manban.auth.application.AdminService.UserView;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Plattform-Admin: Nutzer auflisten und Plattform-Rollen ändern. Session-Auth erforderlich. */
@Tag(name = "Plattform-Verwaltung")
@RestController
class AdminUserController {

  private static final String KEIN_ADMIN = "Der Aufrufer ist kein Plattform-Admin.";

  private static final String UNBEKANNT = "Ein Benutzer mit dieser ID existiert nicht.";

  private static final String ID = "Interne ID des Benutzers, der geändert wird.";

  private final AdminService admin;

  AdminUserController(AdminService admin) {
    this.admin = admin;
  }

  @Operation(
      summary = "Benutzer auflisten",
      description =
          "Liefert alle Konten der Installation mit Plattform-Rolle, Bestätigungs-, Freigabe-"
              + " und Sperrstand.")
  @ApiResponse(responseCode = "200", description = "Alle Konten.")
  @ApiResponse(
      responseCode = "403",
      description = KEIN_ADMIN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PLATTFORM_ADMIN")
  @GetMapping("/api/admin/users")
  List<UserView> list(@AuthenticationPrincipal Long userId) {
    return admin.listUsers(userId);
  }

  @Operation(
      summary = "Plattform-Rolle ändern",
      description =
          "Setzt die Plattform-Rolle eines Benutzers (ADMIN oder USER). Wer zum Admin befördert"
              + " wird, ist damit zugleich freigegeben. Der letzte nicht gesperrte Plattform-Admin"
              + " kann nicht herabgestuft werden.")
  @ApiResponse(responseCode = "200", description = "Der geänderte Benutzer.")
  @ApiResponse(
      responseCode = "400",
      description = "Ungültige Eingabe: platformRole fehlt oder ist unbekannt.",
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
  @ApiResponse(
      responseCode = "404",
      description = UNBEKANNT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description =
          "Die Herabstufung nähme der Installation den letzten nicht gesperrten Plattform-Admin.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PLATTFORM_ADMIN")
  @PatchMapping("/api/admin/users/{id}")
  UserView changeRole(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = ID, example = "7") @PathVariable long id,
      @Valid @RequestBody ChangeRoleRequest request) {
    return admin.changePlatformRole(userId, id, request.platformRole());
  }

  @Operation(
      summary = "Registrierung freigeben",
      description =
          "Gibt ein selbst registriertes Konto frei; erst danach kann es sich anmelden."
              + " Ein schon freigegebenes Konto bleibt unverändert, Zeitpunkt und freigebender"
              + " Admin werden nicht überschrieben.")
  @ApiResponse(responseCode = "200", description = "Der freigegebene Benutzer.")
  @ApiResponse(
      responseCode = "403",
      description = KEIN_ADMIN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = UNBEKANNT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PLATTFORM_ADMIN")
  @PostMapping("/api/admin/users/{id}/approve")
  UserView approve(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = ID, example = "7") @PathVariable long id) {
    return admin.approve(userId, id);
  }

  @Operation(
      summary = "Konto sperren",
      description =
          "Sperrt ein Konto; es kann sich danach nicht mehr anmelden. Ein schon gesperrtes Konto"
              + " bleibt unverändert. Der Aufrufer kann sich nicht selbst sperren, und der letzte"
              + " nicht gesperrte Plattform-Admin kann nicht gesperrt werden.")
  @ApiResponse(responseCode = "200", description = "Der gesperrte Benutzer.")
  @ApiResponse(
      responseCode = "400",
      description = "Der Aufrufer versucht, sich selbst zu sperren.",
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
  @ApiResponse(
      responseCode = "404",
      description = UNBEKANNT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description =
          "Die Sperre nähme der Installation den letzten nicht gesperrten Plattform-Admin.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PLATTFORM_ADMIN")
  @PostMapping("/api/admin/users/{id}/disable")
  UserView disable(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = ID, example = "7") @PathVariable long id) {
    return admin.disable(userId, id);
  }

  @Operation(
      summary = "Konto entsperren",
      description =
          "Hebt die Sperre eines Kontos auf. Ein nicht gesperrtes Konto bleibt unverändert.")
  @ApiResponse(responseCode = "200", description = "Der entsperrte Benutzer.")
  @ApiResponse(
      responseCode = "403",
      description = KEIN_ADMIN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = UNBEKANNT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PLATTFORM_ADMIN")
  @PostMapping("/api/admin/users/{id}/enable")
  UserView enable(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = ID, example = "7") @PathVariable long id) {
    return admin.enable(userId, id);
  }

  @Operation(
      summary = "Anzeigenamen eines Benutzers ändern",
      description =
          "Setzt den Anzeigenamen eines beliebigen Benutzers; führende und folgende Leerzeichen"
              + " werden entfernt.")
  @ApiResponse(responseCode = "200", description = "Der geänderte Benutzer.")
  @ApiResponse(
      responseCode = "400",
      description =
          "Ungültige Eingabe: displayName leer oder länger als 120 Zeichen (Details in"
              + " fieldErrors).",
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
  @ApiResponse(
      responseCode = "404",
      description = UNBEKANNT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PLATTFORM_ADMIN")
  @PatchMapping("/api/admin/users/{id}/display-name")
  UserView changeDisplayName(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = ID, example = "7") @PathVariable long id,
      @Valid @RequestBody ChangeDisplayNameRequest request) {
    return admin.changeDisplayName(userId, id, request.displayName());
  }

  @Schema(description = "Neue Plattform-Rolle eines Benutzers.")
  record ChangeRoleRequest(
      @Schema(description = "Plattform-Rolle: ADMIN oder USER.", example = "USER") @NotNull
          PlatformRole platformRole) {}

  @Schema(description = "Neuer Anzeigename eines Benutzers.")
  record ChangeDisplayNameRequest(
      @Schema(description = "Anzeigename, höchstens 120 Zeichen.", example = "Ada Lovelace")
          @NotBlank
          @Size(max = 120)
          String displayName) {}
}
