package org.mwolff.manban.nightrun.web;

import java.util.List;
import org.mwolff.manban.nightrun.application.DisruptionService;
import org.mwolff.manban.nightrun.application.DisruptionService.DisruptionView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Störungen des Plattform-Leitstands (Issue #1080).
 *
 * <p><b>Der Pfadstamm {@code /api/admin} ist eine Sicherheitsentscheidung, keine
 * Geschmacksfrage</b> (Plan #1072 E24): {@code SecurityConfig} verlangt für {@code /api/admin/**}
 * ausdrücklich eine Sitzung und lässt kein PAT zu, während die Auffangregel {@code /api/**} auch
 * ein ungebundenes Token durchlässt. Ein Pfad wie {@code /api/platform/...} fiele unter die
 * Auffangregel — die Störungsliste aller Projekte samt Quittieren wäre dann mit einem Token
 * erreichbar.
 *
 * <p>Keine eigenen Exceptions: 403 und 404 liefert {@link DisruptionService}.
 */
@RestController
@RequestMapping("/api/admin/disruptions")
class DisruptionController {

  private final DisruptionService disruptions;

  DisruptionController(DisruptionService disruptions) {
    this.disruptions = disruptions;
  }

  @GetMapping
  List<DisruptionView> list(@AuthenticationPrincipal Long userId) {
    return disruptions.disruptions(userId);
  }

  /** Quittieren heißt „gesehen" — ohne Rückfrage, ohne Rückgängig, idempotent (AK 8). */
  @DeleteMapping("/{laufId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void acknowledge(@AuthenticationPrincipal Long userId, @PathVariable long laufId) {
    disruptions.acknowledge(userId, laufId);
  }
}
