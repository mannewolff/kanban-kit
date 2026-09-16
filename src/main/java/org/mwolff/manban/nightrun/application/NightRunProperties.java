package org.mwolff.manban.nightrun.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguration der Nachtlauf-Auswertung (Issue #722).
 *
 * <p>Die Grenze des Ringpuffers steht als Property neben dem Service, nicht in der Web-Schicht und
 * nicht in der Datenbank (Plan #718, A10) — Vorbild ist {@code manban.storage.max-per-card}. Ein
 * Datenbank-Trigger wäre im Test unsichtbar.
 *
 * <p>Läufe und verwaiste Arbeitspakete haben getrennte Grenzen (Issue #966): Seit Issue #964
 * überdauert ein Paket die Verdrängung seines Laufs, und ohne eigene Grenze wüchse {@code
 * night_run_item} unbegrenzt. Pakete eines aufbewahrten Laufs zählen nicht mit — sie fallen erst
 * mit ihrem Lauf.
 *
 * @param maxPerProject Zahl der je Projekt aufbewahrten Läufe; fehlend oder kleiner als 1 ergibt 30
 * @param maxItemsPerProject Zahl der je Projekt aufbewahrten <b>verwaisten</b> Arbeitspakete;
 *     fehlend oder kleiner als 1 ergibt 2000. Bei rund zehn Paketen je Nacht ist das ein halbes
 *     Jahr Rückblick.
 */
@ConfigurationProperties(prefix = "manban.nightrun")
public record NightRunProperties(Integer maxPerProject, Integer maxItemsPerProject) {

  public NightRunProperties {
    if (maxPerProject == null || maxPerProject < 1) {
      maxPerProject = 30;
    }
    if (maxItemsPerProject == null || maxItemsPerProject < 1) {
      maxItemsPerProject = 2000;
    }
  }
}
