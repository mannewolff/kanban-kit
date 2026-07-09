package org.mwolff.manban.auth.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.mwolff.manban.auth.application.AdminService;
import org.mwolff.manban.auth.application.AdminService.UserView;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Plattform-Admin: Nutzer auflisten und Plattform-Rollen ändern. Session-Auth erforderlich. */
@RestController
class AdminUserController {

    private final AdminService admin;

    AdminUserController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/api/admin/users")
    List<UserView> list(@AuthenticationPrincipal Long userId) {
        return admin.listUsers(userId);
    }

    @PatchMapping("/api/admin/users/{id}")
    UserView changeRole(@AuthenticationPrincipal Long userId, @PathVariable long id,
                        @Valid @RequestBody ChangeRoleRequest request) {
        return admin.changePlatformRole(userId, id, request.platformRole());
    }

    record ChangeRoleRequest(@NotNull PlatformRole platformRole) {
    }
}
