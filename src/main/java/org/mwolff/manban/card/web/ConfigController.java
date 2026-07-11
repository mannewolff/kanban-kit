package org.mwolff.manban.card.web;

import org.mwolff.manban.card.application.CleanupProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liefert dem Frontend nicht-sensible App-Konfiguration (z. B. Done-Retention für den
 * Archiv-Countdown).
 */
@RestController
class ConfigController {

  private final CleanupProperties cleanup;

  ConfigController(CleanupProperties cleanup) {
    this.cleanup = cleanup;
  }

  @GetMapping("/api/config")
  ConfigView config() {
    return new ConfigView(cleanup.doneRetentionDays());
  }

  record ConfigView(int doneRetentionDays) {}
}
