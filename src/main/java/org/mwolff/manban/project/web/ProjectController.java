package org.mwolff.manban.project.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
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
@RestController
@RequestMapping("/api/projects")
class ProjectController {

  private final ProjectService projects;
  private final MembershipService memberships;

  ProjectController(ProjectService projects, MembershipService memberships) {
    this.projects = projects;
    this.memberships = memberships;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ProjectView create(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CreateProjectRequest request) {
    return projects.create(userId, request.name(), request.ownerEmail());
  }

  @GetMapping
  List<ProjectView> list(@AuthenticationPrincipal Long userId) {
    return projects.list(userId);
  }

  @PatchMapping("/{id}")
  ProjectView rename(
      @AuthenticationPrincipal Long userId,
      @PathVariable long id,
      @Valid @RequestBody ProjectRequest request) {
    return projects.rename(userId, id, request.name());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@AuthenticationPrincipal Long userId, @PathVariable long id) {
    projects.delete(userId, id);
  }

  /** Überträgt die Eigentümerschaft an ein bestehendes Mitglied (nur der amtierende Owner). */
  @PostMapping("/{id}/owner")
  void transferOwner(
      @AuthenticationPrincipal Long userId,
      @PathVariable long id,
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
  @PutMapping("/{id}/dashboard-participation")
  ProjectView setDashboardParticipation(
      @AuthenticationPrincipal Long userId,
      @PathVariable long id,
      @Valid @RequestBody ParticipationRequest request) {
    return projects.setDashboardParticipation(userId, id, request.participating());
  }

  /** Request-Body für das Umbenennen. */
  record ProjectRequest(@NotBlank @Size(max = 200) String name) {}

  /** Request-Body für das Schalten der Teilnahme am Plattform-Leitstand. */
  record ParticipationRequest(@NotNull Boolean participating) {}

  /** Request-Body für den Eigentümer-Transfer. */
  record TransferOwnerRequest(@NotNull Long newOwnerUserId) {}

  /** Request-Body für das Anlegen: Name + Owner-E-Mail (System-Admin bestimmt den Owner). */
  record CreateProjectRequest(
      @NotBlank @Size(max = 200) String name,
      @NotBlank @Email @Size(max = 254) String ownerEmail) {}
}
