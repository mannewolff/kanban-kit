package org.mwolff.manban.ratelimit.web;

import java.util.List;
import org.mwolff.manban.ratelimit.application.OverloadRejectionService;
import org.mwolff.manban.ratelimit.application.OverloadRejectionService.RejectionView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plattform-Admin: Abweisungen wegen Last je Person und Stunde (Issue #1003). Nur per Sitzung
 * erreichbar ({@code SecurityConfig}, {@code /api/admin/**}); die Admin-Prüfung selbst erledigt der
 * {@link OverloadRejectionService}.
 */
@RestController
class AdminRejectionController {

  private final OverloadRejectionService service;

  AdminRejectionController(OverloadRejectionService service) {
    this.service = service;
  }

  @GetMapping("/api/admin/overload-rejections")
  List<RejectionView> list(@AuthenticationPrincipal Long userId) {
    return service.list(userId);
  }
}
