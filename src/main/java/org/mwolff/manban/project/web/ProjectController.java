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
import java.util.List;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.mwolff.manban.project.application.MembershipService;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.project.application.ProjectService.ProjectView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Projekt-Verwaltung. Owner-Isolation über Mitgliedschaft (Nichtmitglied → 404). */
@Tag(
    name = "Projekte",
    description =
        "Projekte anlegen, auflisten, umbenennen und löschen, die Eigentümerschaft übertragen und"
            + " die Teilnahme am Plattform-Leitstand schalten. Ein Projekt bündelt Boards, Karten"
            + " und Mitglieder; jedes Mitglied hat darin eine Projekt-Rolle (OWNER, ADMIN, MEMBER,"
            + " VIEWER), aus der seine Rechte folgen. Wer kein Mitglied ist, bekommt 404 statt"
            + " 403 — so verrät der Leitstand nicht, ob es ein Projekt gibt. Ein Plattform-Admin"
            + " hat in jedem Projekt Vollzugriff.")
@RestController
@RequestMapping("/api/projects")
class ProjectController {

  private final ProjectService projects;
  private final MembershipService memberships;

  private static final String BESCHREIBUNG_PROJECT_ID = "Interne ID des Projekts.";

  private static final String PROJEKT_FEHLT =
      "Das Projekt gibt es nicht, oder der Aufrufer ist kein Mitglied.";

  ProjectController(ProjectService projects, MembershipService memberships) {
    this.projects = projects;
    this.memberships = memberships;
  }

  @Operation(
      summary = "Projekt anlegen",
      description =
          "Legt ein Projekt an — nur für Plattform-Admins. Der Owner wird über seine E-Mail"
              + " bestimmt und als Mitglied mit der Rolle OWNER eingetragen; zugleich entsteht"
              + " ein erstes Board. Der Owner erhält eine Info-Mail. Die Antwort bestätigt das"
              + " gespeicherte Projekt, nicht die Zustellung der Mail: Die wird nach dem Speichern"
              + " mit Wiederholungen versandt.")
  @ApiResponse(
      responseCode = "201",
      description = "Das angelegte Projekt, mit der Rolle OWNER aus Sicht des Owners.")
  @ApiResponse(
      responseCode = "400",
      description =
          "Ungültige Eingabe: name oder ownerEmail leer, zu lang oder keine E-Mail (Details in"
              + " fieldErrors), oder zu ownerEmail gibt es keinen Benutzer.",
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
  @ApiResponse(
      responseCode = "422",
      description = "Der Benutzer zu ownerEmail ist noch nicht freigegeben.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PLATTFORM_ADMIN")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ProjectView create(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CreateProjectRequest request) {
    return projects.create(userId, request.name(), request.ownerEmail());
  }

  @Operation(
      summary = "Projekte auflisten",
      description =
          "Liefert die Projekte, in denen der Aufrufer Mitglied ist, jeweils mit seiner Rolle."
              + " Ein Plattform-Admin sieht alle Projekte; wo er kein Mitglied ist, steht dort"
              + " die Rolle OWNER, weil er darin Vollzugriff hat.")
  @ApiResponse(responseCode = "200", description = "Die sichtbaren Projekte.")
  @GetMapping
  List<ProjectView> list(@AuthenticationPrincipal Long userId) {
    return projects.list(userId);
  }

  @Operation(summary = "Projekt umbenennen", description = "Setzt den Namen des Projekts.")
  @ApiResponse(responseCode = "200", description = "Das umbenannte Projekt.")
  @ApiResponse(
      responseCode = "400",
      description =
          "Ungültige Eingabe: name leer oder länger als 200 Zeichen (Details in" + " fieldErrors).",
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
  @PatchMapping("/{id}")
  ProjectView rename(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_PROJECT_ID, example = "1") @PathVariable long id,
      @Valid @RequestBody ProjectRequest request) {
    return projects.rename(userId, id, request.name());
  }

  @Operation(
      summary = "Projekt löschen",
      description =
          "Löscht das Projekt samt Boards, Karten, Vorhaben und Mitgliedschaften — nur für"
              + " Plattform-Admins. Das Löschen lässt sich nicht rückgängig machen.")
  @ApiResponse(responseCode = "204", description = "Das Projekt ist gelöscht.")
  @ApiResponse(
      responseCode = "403",
      description = "Der Aufrufer ist kein Plattform-Admin.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PLATTFORM_ADMIN")
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_PROJECT_ID, example = "1") @PathVariable long id) {
    projects.delete(userId, id);
  }

  /** Überträgt die Eigentümerschaft an ein bestehendes Mitglied (nur der amtierende Owner). */
  @Operation(
      summary = "Eigentümerschaft übertragen",
      description =
          "Macht ein bestehendes Mitglied zum OWNER; der aufrufende bisherige Owner wird ADMIN."
              + " Ist das Ziel schon OWNER, ändert sich nichts. Weitere Owner bleiben unberührt.")
  @ApiResponse(responseCode = "200", description = "Die Eigentümerschaft ist übertragen.")
  @ApiResponse(
      responseCode = "400",
      description = "Ungültige Eingabe: newOwnerUserId fehlt (Details in fieldErrors).",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = "Die Projekt-Rolle des Aufrufers umfasst PROJECT_OWNER_TRANSFER nicht.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = PROJEKT_FEHLT + " Ebenso: newOwnerUserId ist kein Mitglied des Projekts.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PROJECT_OWNER_TRANSFER")
  @PostMapping("/{id}/owner")
  void transferOwner(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_PROJECT_ID, example = "1") @PathVariable long id,
      @Valid @RequestBody TransferOwnerRequest request) {
    memberships.transferOwnership(userId, id, request.newOwnerUserId());
  }

  /**
   * Schaltet die Teilnahme des Projekts am Plattform-Leitstand (Issue #1077, Plan #1072 E6).
   *
   * <p>Eigener Endpunkt und kein zweites Feld am {@code PATCH /{id}}: Jener Weg ist das Umbenennen
   * und lässt einen Plattform-Admin passieren; dieser darf ihn ausdrücklich nicht passieren lassen
   * (AK 16). Zwei gegenläufige Rechteregeln an einem Endpunkt wären eine Abzweigung, die man beim
   * Lesen übersieht.
   */
  @Operation(
      summary = "Teilnahme am Plattform-Leitstand schalten",
      description =
          "Der Plattform-Leitstand ist die projektübergreifende Auswertung der Läufe für"
              + " Plattform-Admins. Ein Projekt erscheint dort nur, wenn es teilnimmt; das ist"
              + " seine Einwilligung. Schalten dürfen deshalb allein echte Mitglieder mit der"
              + " Rolle OWNER oder ADMIN — ein Plattform-Admin ohne eigene Mitgliedschaft nicht.")
  @ApiResponse(responseCode = "200", description = "Das Projekt mit dem neuen Stand.")
  @ApiResponse(
      responseCode = "400",
      description = "Ungültige Eingabe: participating fehlt (Details in fieldErrors).",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = "Die Rolle des Aufrufers im Projekt ist weder OWNER noch ADMIN.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description =
          "Das Projekt gibt es nicht, oder der Aufrufer ist kein echtes Mitglied (auch als"
              + " Plattform-Admin).",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @PutMapping("/{id}/dashboard-participation")
  ProjectView setDashboardParticipation(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_PROJECT_ID, example = "1") @PathVariable long id,
      @Valid @RequestBody ParticipationRequest request) {
    return projects.setDashboardParticipation(userId, id, request.participating());
  }

  /** Request-Body für das Umbenennen. */
  @Schema(description = "Neuer Name eines Projekts.")
  record ProjectRequest(
      @Schema(description = "Name, höchstens 200 Zeichen.", example = "Leitstand")
          @NotBlank
          @Size(max = 200)
          String name) {}

  /** Request-Body für das Schalten der Teilnahme am Plattform-Leitstand. */
  @Schema(description = "Teilnahme am Plattform-Leitstand.")
  record ParticipationRequest(
      @Schema(description = "true: Das Projekt nimmt teil.", example = "true") @NotNull
          Boolean participating) {}

  /** Request-Body für den Eigentümer-Transfer. */
  @Schema(description = "Künftiger Owner eines Projekts.")
  record TransferOwnerRequest(
      @Schema(description = "Benutzer-ID eines bestehenden Mitglieds.", example = "7") @NotNull
          Long newOwnerUserId) {}

  /** Request-Body für das Anlegen: Name + Owner-E-Mail (System-Admin bestimmt den Owner). */
  @Schema(description = "Name und Owner eines neuen Projekts.")
  record CreateProjectRequest(
      @Schema(description = "Name, höchstens 200 Zeichen.", example = "Leitstand")
          @NotBlank
          @Size(max = 200)
          String name,
      @Schema(
              description = "E-Mail eines freigegebenen Benutzers, der Owner wird.",
              example = "owner@example.org")
          @NotBlank
          @Email
          @Size(max = 254)
          String ownerEmail) {}
}
