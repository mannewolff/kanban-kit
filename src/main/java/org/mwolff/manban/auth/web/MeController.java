package org.mwolff.manban.auth.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.mwolff.manban.auth.application.MeService;
import org.mwolff.manban.auth.application.MeService.MeView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Selbstauskunft und Profilpflege des angemeldeten Benutzers. */
@RestController
class MeController {

  private final MeService meService;

  MeController(MeService meService) {
    this.meService = meService;
  }

  @GetMapping("/api/me")
  MeView me(@AuthenticationPrincipal Long userId) {
    return meService.load(userId);
  }

  @PatchMapping("/api/me")
  MeView updateProfile(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody UpdateProfileRequest request) {
    return meService.updateDisplayName(userId, request.displayName());
  }

  record UpdateProfileRequest(@NotBlank @Size(max = 120) String displayName) {}
}
