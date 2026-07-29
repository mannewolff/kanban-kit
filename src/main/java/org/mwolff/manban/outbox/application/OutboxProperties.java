package org.mwolff.manban.outbox.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguration der transaktionalen Outbox (Issue #501). Wrapper-Typen statt Primitiven, damit
 * „nicht gesetzt" ({@code null}) vom Default unterscheidbar bleibt.
 *
 * @param enabled ob der Worker läuft; abgeschaltet bleiben Einträge liegen (gehen nicht verloren)
 * @param pollIntervalMs Abstand zwischen zwei Worker-Läufen
 * @param batchSize wie viele fällige Einträge ein Lauf höchstens greift
 * @param maxAttempts Versuche, nach denen ein Eintrag endgültig als gescheitert gilt
 * @param retryBaseDelay Wartezeit vor dem zweiten Versuch; verdoppelt sich danach je Versuch
 * @param retryMaxDelay Obergrenze der Wartezeit zwischen zwei Versuchen
 * @param completedRetentionDays Tage, nach denen erledigte Einträge gelöscht werden
 */
@ConfigurationProperties(prefix = "manban.outbox")
public record OutboxProperties(
    Boolean enabled,
    Long pollIntervalMs,
    Integer batchSize,
    Integer maxAttempts,
    Duration retryBaseDelay,
    Duration retryMaxDelay,
    Integer completedRetentionDays) {

  public OutboxProperties {
    if (enabled == null) {
      enabled = Boolean.TRUE;
    }
    if (pollIntervalMs == null || pollIntervalMs < 1) {
      pollIntervalMs = 5000L;
    }
    if (batchSize == null || batchSize < 1) {
      batchSize = 50;
    }
    if (maxAttempts == null || maxAttempts < 1) {
      maxAttempts = 8;
    }
    if (retryBaseDelay == null || retryBaseDelay.isZero() || retryBaseDelay.isNegative()) {
      retryBaseDelay = Duration.ofSeconds(10);
    }
    if (retryMaxDelay == null || retryMaxDelay.isZero() || retryMaxDelay.isNegative()) {
      retryMaxDelay = Duration.ofHours(1);
    }
    if (completedRetentionDays == null || completedRetentionDays < 1) {
      completedRetentionDays = 7;
    }
  }
}
