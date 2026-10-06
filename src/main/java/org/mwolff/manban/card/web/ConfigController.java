package org.mwolff.manban.card.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.mwolff.manban.card.application.DoneRetentionSettingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liefert dem Frontend nicht-sensible App-Konfiguration (z. B. Done-Retention für den
 * Archiv-Countdown). Der Wert ist der effektive (global gesetzter Override oder Env-Default) und
 * kann {@code 0} sein (= Auto-Archiv aus).
 */
@Tag(
    name = "Konfiguration",
    description = "Nicht vertrauliche Einstellungen des Leitstands, die die Oberfläche braucht.")
@RestController
class ConfigController {

  private final DoneRetentionSettingService retentionSetting;

  ConfigController(DoneRetentionSettingService retentionSetting) {
    this.retentionSetting = retentionSetting;
  }

  @Operation(
      summary = "Konfiguration lesen",
      description =
          "Liefert die nicht vertraulichen Einstellungen des Leitstands, derzeit die"
              + " Done-Aufbewahrung: Nach so vielen Tagen in der Done-Spalte wird eine Karte"
              + " automatisch archiviert. Der Wert ist der wirksame — von der Plattform-Verwaltung"
              + " gesetzt oder der Vorgabewert des Betriebs; 0 heißt, dass nicht automatisch"
              + " archiviert wird.")
  @ApiResponse(responseCode = "200", description = "Die Einstellungen.")
  @GetMapping("/api/config")
  ConfigView config() {
    return new ConfigView(retentionSetting.effectiveRetentionDays());
  }

  @Schema(description = "Nicht vertrauliche Einstellungen des Leitstands.")
  record ConfigView(
      @Schema(
              description =
                  "Wirksame Done-Aufbewahrung in Tagen; 0 heißt kein automatisches Archivieren.",
              example = "14")
          int doneRetentionDays) {}
}
