package org.mwolff.manban.project.web;

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
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.mwolff.manban.project.application.MembershipService;
import org.mwolff.manban.project.application.MembershipService.MemberView;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Mitgliederverwaltung eines Projekts. Rolle ändern/entfernen erfordert MEMBER_REMOVE. */
@Tag(
    name = "Mitglieder & Einladungen",
    description =
        "Mitglieder eines Projekts auflisten, ihre Projekt-Rolle und ihren Anzeigenamen ändern,"
            + " sie entfernen, sowie Benutzer einladen und Einladungen annehmen. Ein Projekt"
            + " behält immer mindestens einen OWNER: Wer den letzten degradieren oder entfernen"
            + " will, bekommt 409.")
@RestController
@RequestMapping("/api/projects/{projectId}/members")
class MemberController {

  private final MembershipService memberships;

  private static final String BESCHREIBUNG_PROJECT_ID = "Interne ID des Projekts.";

  private static final String BESCHREIBUNG_TARGET_USER_ID = "Benutzer-ID des Mitglieds.";

  private static final String VERBOTEN =
      "Die Projekt-Rolle des Aufrufers umfasst MEMBER_REMOVE nicht.";

  private static final String MITGLIED_FEHLT =
      "Das Projekt gibt es nicht, der Aufrufer ist kein Mitglied, oder targetUserId ist kein"
          + " Mitglied des Projekts.";

  private static final String LETZTER_OWNER =
      "Das Mitglied ist der letzte OWNER des Projekts und kann weder degradiert noch entfernt"
          + " werden.";

  MemberController(MembershipService memberships) {
    this.memberships = memberships;
  }

  @Operation(
      summary = "Mitglieder auflisten",
      description =
          "Liefert alle Mitglieder des Projekts mit ihrer Rolle. Jedes Mitglied darf"
              + " die Liste sehen.")
  @ApiResponse(responseCode = "200", description = "Die Mitglieder des Projekts.")
  @ApiResponse(
      responseCode = "404",
      description = "Das Projekt gibt es nicht, oder der Aufrufer ist kein Mitglied.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping
  List<MemberView> list(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_PROJECT_ID, example = "1") @PathVariable
          long projectId) {
    return memberships.listMembers(userId, projectId);
  }

  @Operation(
      summary = "Rolle eines Mitglieds ändern",
      description = "Setzt die Projekt-Rolle des Mitglieds (OWNER, ADMIN, MEMBER oder VIEWER).")
  @ApiResponse(responseCode = "200", description = "Das Mitglied mit seiner neuen Rolle.")
  @ApiResponse(
      responseCode = "400",
      description = "Ungültige Eingabe: role fehlt oder ist keine bekannte Rolle.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = MITGLIED_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description = LETZTER_OWNER,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "MEMBER_REMOVE")
  @PatchMapping("/{targetUserId}")
  MemberView changeRole(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_PROJECT_ID, example = "1") @PathVariable long projectId,
      @Parameter(description = BESCHREIBUNG_TARGET_USER_ID, example = "7") @PathVariable
          long targetUserId,
      @Valid @RequestBody ChangeRoleRequest request) {
    return memberships.changeRole(userId, projectId, targetUserId, request.role());
  }

  @Operation(
      summary = "Anzeigenamen eines Mitglieds ändern",
      description =
          "Setzt den Anzeigenamen des Benutzers. Achtung: Es gibt keinen Namen je Projekt —"
              + " geändert wird der Name des Benutzers in allen Projekten.")
  @ApiResponse(responseCode = "200", description = "Das Mitglied mit seinem neuen Namen.")
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
      description = VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = MITGLIED_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "MEMBER_REMOVE")
  @PatchMapping("/{targetUserId}/display-name")
  MemberView changeDisplayName(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_PROJECT_ID, example = "1") @PathVariable long projectId,
      @Parameter(description = BESCHREIBUNG_TARGET_USER_ID, example = "7") @PathVariable
          long targetUserId,
      @Valid @RequestBody ChangeDisplayNameRequest request) {
    return memberships.changeMemberDisplayName(
        userId, projectId, targetUserId, request.displayName());
  }

  @Operation(summary = "Mitglied entfernen", description = "Entfernt den Benutzer aus dem Projekt.")
  @ApiResponse(responseCode = "204", description = "Das Mitglied ist entfernt.")
  @ApiResponse(
      responseCode = "403",
      description = VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = MITGLIED_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description = LETZTER_OWNER,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "MEMBER_REMOVE")
  @DeleteMapping("/{targetUserId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void remove(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_PROJECT_ID, example = "1") @PathVariable long projectId,
      @Parameter(description = BESCHREIBUNG_TARGET_USER_ID, example = "7") @PathVariable
          long targetUserId) {
    memberships.removeMember(userId, projectId, targetUserId);
  }

  @Schema(description = "Neue Projekt-Rolle eines Mitglieds.")
  record ChangeRoleRequest(
      @Schema(description = "Projekt-Rolle.", example = "MEMBER") @NotNull ProjectRole role) {}

  @Schema(description = "Neuer Anzeigename eines Benutzers.")
  record ChangeDisplayNameRequest(
      @Schema(description = "Anzeigename, höchstens 120 Zeichen.", example = "Erika Muster")
          @NotBlank
          @Size(max = 120)
          String displayName) {}
}
