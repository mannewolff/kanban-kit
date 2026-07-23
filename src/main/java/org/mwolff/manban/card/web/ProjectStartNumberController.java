package org.mwolff.manban.card.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.mwolff.manban.card.application.ProjectStartNumberService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Projektweite Startnummer: den effektiven nächsten Wert lesen (Vorbelegung im Editiermodus) und —
 * für Owner/Edit-Berechtigte — neu setzen. Liegt im card-Modul (Nummerierung ist Karten-Belange;
 * project→card wäre ein Modul-Zyklus).
 */
@RestController
class ProjectStartNumberController {

  private final ProjectStartNumberService service;

  ProjectStartNumberController(ProjectStartNumberService service) {
    this.service = service;
  }

  @GetMapping("/api/projects/{projectId}/next-card-number")
  NextCardNumberView get(@AuthenticationPrincipal Long userId, @PathVariable long projectId) {
    return new NextCardNumberView(service.effectiveNextCardNumber(userId, projectId));
  }

  @PutMapping("/api/projects/{projectId}/next-card-number")
  NextCardNumberView set(
      @AuthenticationPrincipal Long userId,
      @PathVariable long projectId,
      @Valid @RequestBody NextCardNumberRequest request) {
    return new NextCardNumberView(
        service.setNextCardNumber(userId, projectId, request.nextCardNumber()));
  }

  record NextCardNumberRequest(@Min(1) int nextCardNumber) {}

  record NextCardNumberView(int nextCardNumber) {}
}
