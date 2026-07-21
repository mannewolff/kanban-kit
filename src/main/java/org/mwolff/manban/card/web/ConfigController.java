package org.mwolff.manban.card.web;

import org.mwolff.manban.card.application.DoneRetentionSettingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liefert dem Frontend nicht-sensible App-Konfiguration (z. B. Done-Retention für den
 * Archiv-Countdown). Der Wert ist der effektive (global gesetzter Override oder Env-Default) und
 * kann {@code 0} sein (= Auto-Archiv aus).
 */
@RestController
class ConfigController {

  private final DoneRetentionSettingService retentionSetting;

  ConfigController(DoneRetentionSettingService retentionSetting) {
    this.retentionSetting = retentionSetting;
  }

  @GetMapping("/api/config")
  ConfigView config() {
    return new ConfigView(retentionSetting.effectiveRetentionDays());
  }

  record ConfigView(int doneRetentionDays) {}
}
