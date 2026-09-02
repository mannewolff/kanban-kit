package org.mwolff.manban.nightrun.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguration der Nachtlauf-Auswertung (Issue #722).
 *
 * <p>Die Grenze des Ringpuffers steht als Property neben dem Service, nicht in der Web-Schicht und
 * nicht in der Datenbank (Plan #718, A10) — Vorbild ist {@code manban.storage.max-per-card}. Ein
 * Datenbank-Trigger wäre im Test unsichtbar.
 *
 * @param maxPerProject Zahl der je Projekt aufbewahrten Läufe; fehlend oder kleiner als 1 ergibt 30
 */
@ConfigurationProperties(prefix = "manban.nightrun")
public record NightRunProperties(Integer maxPerProject) {

  public NightRunProperties {
    if (maxPerProject == null || maxPerProject < 1) {
      maxPerProject = 30;
    }
  }
}
