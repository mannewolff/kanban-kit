package org.mwolff.manban.auth.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.mwolff.manban.auth.application.AdminService.UserView;
import org.mwolff.manban.auth.application.BootstrapService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-Bootstrap: hebt den eingeloggten Nutzer per Env-Token zum ersten Plattform-Admin.
 * Session-Auth erforderlich (über SecurityConfig /api/admin/**), aber KEIN Admin-Recht — sonst
 * könnte niemand je der erste Admin werden.
 */
@RestController
class BootstrapController {

  private final BootstrapService bootstrapService;

  BootstrapController(BootstrapService bootstrapService) {
    this.bootstrapService = bootstrapService;
  }

  @PostMapping("/api/admin/bootstrap")
  UserView bootstrap(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody BootstrapRequest request) {
    return bootstrapService.bootstrap(userId, request.token());
  }

  record BootstrapRequest(@NotBlank String token) {}
}
