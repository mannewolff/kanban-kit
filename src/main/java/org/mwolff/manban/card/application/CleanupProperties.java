package org.mwolff.manban.card.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguration der Done-Retention.
 *
 * @param enabled ob der geplante Aufräum-Job aktiv ist
 * @param doneRetentionDays Tage, nach denen eine Done-Karte automatisch archiviert wird
 * @param cron Cron-Ausdruck für den Job
 */
@ConfigurationProperties(prefix = "manban.cleanup")
public record CleanupProperties(Boolean enabled, Integer doneRetentionDays, String cron) {

  public CleanupProperties {
    if (enabled == null) {
      enabled = Boolean.TRUE;
    }
    if (doneRetentionDays == null || doneRetentionDays < 1) {
      doneRetentionDays = 30;
    }
    if (cron == null || cron.isBlank()) {
      cron = "0 0 * * * *"; // stündlich
    }
  }
}
