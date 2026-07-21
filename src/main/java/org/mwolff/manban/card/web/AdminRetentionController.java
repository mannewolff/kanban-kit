package org.mwolff.manban.card.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.DoneRetentionSettingService;
import org.mwolff.manban.card.application.DoneRetentionSettingService.RetentionSettings;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plattform-Admin: die globale Done-Aufbewahrung anzeigen und ändern. Session-Auth erforderlich
 * (siehe {@code SecurityConfig}, {@code /api/admin/**}); die Admin-Autorisierung selbst erledigt
 * der {@link DoneRetentionSettingService}.
 */
@RestController
class AdminRetentionController {

  private final DoneRetentionSettingService service;

  AdminRetentionController(DoneRetentionSettingService service) {
    this.service = service;
  }

  @GetMapping("/api/admin/done-retention")
  RetentionView get(@AuthenticationPrincipal Long userId) {
    return toView(service.currentFor(userId));
  }

  @PutMapping("/api/admin/done-retention")
  RetentionView update(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody UpdateRetentionRequest request) {
    return toView(service.updateOverride(userId, request.days()));
  }

  private static RetentionView toView(RetentionSettings s) {
    return new RetentionView(s.effective(), s.override());
  }

  /**
   * {@code 0} = Auto-Archiv aus ist erlaubt; negative Werte lehnt die Bean-Validation mit 400 ab.
   */
  record UpdateRetentionRequest(@NotNull @Min(0) Integer days) {}

  record RetentionView(int effective, @Nullable Integer override) {}
}
