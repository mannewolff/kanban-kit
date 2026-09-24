package org.mwolff.manban.nightrun.web;

import java.time.ZoneId;
import org.mwolff.manban.nightrun.application.DisruptionService;
import org.mwolff.manban.nightrun.application.DisruptionService.LeitstandView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Der Plattform-Leitstand an HTTP (Issue #1080, auf einen Endpunkt für drei Listen umgestellt in
 * #1095).
 *
 * <p><b>Der Pfadstamm {@code /api/admin} ist eine Sicherheitsentscheidung, keine
 * Geschmacksfrage</b> (Plan #1072 E24): {@code SecurityConfig} verlangt für {@code /api/admin/**}
 * ausdrücklich eine Sitzung und lässt kein PAT zu, während die Auffangregel {@code /api/**} auch
 * ein ungebundenes Token durchlässt. Ein Pfad wie {@code /api/platform/...} fiele unter die
 * Auffangregel — die Läufe und Störungen aller Projekte samt Quittieren wären dann mit einem Token
 * erreichbar.
 *
 * <p><b>Ein Abruf für die Ansicht, ein eigener fürs Quittieren</b> (Plan #1088 E5): Die drei Listen
 * kommen in einer Antwort, weil die Seite sich auffrischt und ein Lauf zwischen zwei Rundreisen den
 * Bereich wechseln kann. Das Quittieren bleibt daneben — es ist eine Einzelaktion, keine Ansicht.
 *
 * <p>Keine eigenen Exceptions: 403 und 404 liefert {@link DisruptionService}, 400 die Bindung und
 * die Abweisung der Zone über {@link Regionszone}.
 */
@RestController
@RequestMapping("/api/admin")
class DisruptionController {

  private final DisruptionService disruptions;

  DisruptionController(DisruptionService disruptions) {
    this.disruptions = disruptions;
  }

  /**
   * Die drei Listen der Ansicht. Die Zone kommt vom Browser (Plan #1088 E6) — im Container läuft
   * die JVM regelmäßig in UTC, und die Nachtgrenze läge dann um Stunden verschoben.
   */
  @GetMapping("/leitstand")
  LeitstandView leitstand(@AuthenticationPrincipal Long userId, @RequestParam ZoneId zone) {
    return disruptions.leitstand(userId, Regionszone.of(zone));
  }

  /** Quittieren heißt „gesehen" — ohne Rückfrage, ohne Rückgängig, idempotent (AK 8). */
  @DeleteMapping("/disruptions/{laufId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void acknowledge(@AuthenticationPrincipal Long userId, @PathVariable long laufId) {
    disruptions.acknowledge(userId, laufId);
  }

  /**
   * Kennzeichnet einen hängenden Lauf von Hand als beendet (Issue #1197).
   *
   * <p><b>{@code POST} und nicht {@code DELETE}</b> wie das Quittieren daneben: Gelöscht wird
   * nichts — der Lauf bleibt mitsamt seinen Paketen stehen und bekommt einen Vermerk. Der eigene
   * Pfadstamm {@code /night-runs} sagt dasselbe: Dies ist eine Aussage über den <em>Lauf</em>,
   * während die Quittung eine über die Sichtung einer Störung ist.
   *
   * <p>404, 409 und 403 liefert {@link DisruptionService}.
   */
  @PostMapping("/night-runs/{laufId}/close")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void close(@AuthenticationPrincipal Long userId, @PathVariable long laufId) {
    disruptions.close(userId, laufId);
  }
}
