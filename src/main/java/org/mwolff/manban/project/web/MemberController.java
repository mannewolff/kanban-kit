package org.mwolff.manban.project.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
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
@RestController
@RequestMapping("/api/projects/{projectId}/members")
class MemberController {

    private final MembershipService memberships;

    MemberController(MembershipService memberships) {
        this.memberships = memberships;
    }

    @GetMapping
    List<MemberView> list(@AuthenticationPrincipal Long userId, @PathVariable long projectId) {
        return memberships.listMembers(userId, projectId);
    }

    @PatchMapping("/{targetUserId}")
    MemberView changeRole(@AuthenticationPrincipal Long userId, @PathVariable long projectId,
                          @PathVariable long targetUserId, @Valid @RequestBody ChangeRoleRequest request) {
        return memberships.changeRole(userId, projectId, targetUserId, request.role());
    }

    @DeleteMapping("/{targetUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@AuthenticationPrincipal Long userId, @PathVariable long projectId,
                @PathVariable long targetUserId) {
        memberships.removeMember(userId, projectId, targetUserId);
    }

    record ChangeRoleRequest(@NotNull ProjectRole role) {
    }
}
