package org.mwolff.manban.project.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@RestController
class InvitationController {

  private final MembershipService memberships;

  InvitationController(MembershipService memberships) {
    this.memberships = memberships;
  }

  @PostMapping("/api/projects/{id}/invitations")
  @ResponseStatus(HttpStatus.ACCEPTED)
  InviteResponse invite(
      @AuthenticationPrincipal Long userId,
      @PathVariable long id,
      @Valid @RequestBody InviteRequest request) {
    InviteOutcome outcome = memberships.invite(userId, id, request.email(), request.role());
    return new InviteResponse(outcome.status());
  }

  @PostMapping("/api/invitations/accept")
  MemberView accept(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody AcceptInvitationRequest request) {
    return memberships.accept(userId, request.token());
  }

  record InviteRequest(@NotBlank @Email @Size(max = 320) String email, @NotNull ProjectRole role) {}

  /** Ergebnis der Zuordnung: {@code "added"} (direkt Mitglied) oder {@code "invited"}. */
  record InviteResponse(String status) {}

  record AcceptInvitationRequest(@NotBlank String token) {}
}
