package org.mwolff.manban.project.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.mwolff.manban.project.application.InviteOutcome;
import org.mwolff.manban.project.application.MembershipService;
import org.mwolff.manban.project.application.MembershipService.MemberView;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Projekt-Einladungen: erstellen (mit MEMBER_INVITE) und annehmen. */
@Tag(name = "Mitglieder & Einladungen")
@RestController
class InvitationController {

  private final MembershipService memberships;

  InvitationController(MembershipService memberships) {
    this.memberships = memberships;
  }

  @Operation(
      summary = "Benutzer einladen",
      description =
          "Ordnet eine E-Mail dem Projekt mit der angegebenen Rolle zu. Gehört sie zu einem"
              + " registrierten und freigegebenen Benutzer, wird er sofort Mitglied (status"
              + " added); ist er es schon, bekommt er die neue Rolle. Sonst geht ein"
              + " Einladungslink per Mail an die Adresse (status invited). Die Antwort bestätigt"
              + " die gespeicherte Mitgliedschaft bzw. Einladung, nicht die Zustellung der Mail.")
  @ApiResponse(
      responseCode = "202",
      description = "Die Zuordnung ist gespeichert; status sagt, auf welchem Weg.")
  @ApiResponse(
      responseCode = "400",
      description =
          "Ungültige Eingabe: email leer, zu lang oder keine E-Mail, oder role fehlt bzw. ist"
              + " unbekannt.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = "Die Projekt-Rolle des Aufrufers umfasst MEMBER_INVITE nicht.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = "Das Projekt gibt es nicht, oder der Aufrufer ist kein Mitglied.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description =
          "Die E-Mail gehört dem letzten OWNER des Projekts, und die neue Rolle ist nicht OWNER.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "422",
      description = "Der Benutzer zur E-Mail ist registriert, aber noch nicht freigegeben.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "MEMBER_INVITE")
  @PostMapping("/api/projects/{id}/invitations")
  @ResponseStatus(HttpStatus.ACCEPTED)
  InviteResponse invite(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Interne ID des Projekts.", example = "1") @PathVariable long id,
      @Valid @RequestBody InviteRequest request) {
    InviteOutcome outcome = memberships.invite(userId, id, request.email(), request.role());
    return new InviteResponse(outcome.status());
  }

  @Operation(
      summary = "Einladung annehmen",
      description =
          "Macht den angemeldeten Benutzer mit dem Token aus dem Einladungslink zum Mitglied des"
              + " Projekts, mit der Rolle aus der Einladung. Die E-Mail des Benutzers muss die"
              + " eingeladene sein. Ein Token gilt nur einmal und nur bis zu seinem Ablauf.")
  @ApiResponse(
      responseCode = "200",
      description = "Die neue (oder schon bestehende) Mitgliedschaft.")
  @ApiResponse(
      responseCode = "400",
      description =
          "Ungültige Eingabe: token fehlt, ist unbekannt, abgelaufen oder schon eingelöst.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = "Die Einladung gilt einer anderen E-Mail als der des Aufrufers.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @PostMapping("/api/invitations/accept")
  MemberView accept(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody AcceptInvitationRequest request) {
    return memberships.accept(userId, request.token());
  }

  @Schema(description = "Einzuladende E-Mail und ihre künftige Projekt-Rolle.")
  record InviteRequest(
      @Schema(description = "E-Mail, höchstens 320 Zeichen.", example = "erika@example.org")
          @NotBlank
          @Email
          @Size(max = 320)
          String email,
      @Schema(description = "Projekt-Rolle.", example = "MEMBER") @NotNull ProjectRole role) {}

  /** Ergebnis der Zuordnung: {@code "added"} (direkt Mitglied) oder {@code "invited"}. */
  @Schema(description = "Ergebnis der Zuordnung.")
  record InviteResponse(
      @Schema(
              description =
                  "added: Der Benutzer ist sofort Mitglied. invited: Ein Einladungslink ging per"
                      + " Mail an die Adresse.",
              allowableValues = {"added", "invited"},
              example = "invited")
          String status) {}

  @Schema(description = "Token aus dem Einladungslink.")
  record AcceptInvitationRequest(
      @Schema(description = "Token aus dem Parameter token des Einladungslinks.") @NotBlank
          String token) {}
}
