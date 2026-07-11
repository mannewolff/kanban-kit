package org.mwolff.manban.attachment.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Objektspeicher-Konfiguration (S3-kompatibel, MinIO).
 *
 * @param endpoint MinIO-/S3-Endpoint
 * @param accessKey Zugangsschlüssel
 * @param secretKey geheimer Schlüssel
 * @param bucket Ziel-Bucket für Anhänge
 * @param maxPerCard maximale Anzahl Anhänge pro Karte
 */
@ConfigurationProperties(prefix = "manban.storage")
public record ObjectStorageProperties(
    String endpoint, String accessKey, String secretKey, String bucket, Integer maxPerCard) {

  public ObjectStorageProperties {
    if (endpoint == null || endpoint.isBlank()) {
      endpoint = "http://localhost:9000";
    }
    if (accessKey == null || accessKey.isBlank()) {
      accessKey = "manban";
    }
    if (secretKey == null || secretKey.isBlank()) {
      secretKey = "manban-minio";
    }
    if (bucket == null || bucket.isBlank()) {
      bucket = "manban";
    }
    if (maxPerCard == null || maxPerCard < 1) {
      maxPerCard = 20;
    }
  }
}
