package org.mwolff.manban.card.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguration der Aufräum-Jobs (Done-Retention und Papierkorb-Retention).
 *
 * @param enabled ob die geplanten Aufräum-Jobs aktiv sind
 * @param doneRetentionDays Tage, nach denen eine Done-Karte automatisch archiviert wird
 * @param trashRetentionDays Tage, nach denen eine Papierkorb-Karte endgültig gelöscht wird
 * @param cron Cron-Ausdruck für die Jobs
 */
@ConfigurationProperties(prefix = "manban.cleanup")
public record CleanupProperties(
    Boolean enabled, Integer doneRetentionDays, Integer trashRetentionDays, String cron) {

  public CleanupProperties {
    if (enabled == null) {
      enabled = Boolean.TRUE;
    }
    if (doneRetentionDays == null || doneRetentionDays < 1) {
      doneRetentionDays = 30;
    }
    if (trashRetentionDays == null || trashRetentionDays < 1) {
      trashRetentionDays = 30;
    }
    if (cron == null || cron.isBlank()) {
      cron = "0 0 * * * *"; // stündlich
    }
  }
}
