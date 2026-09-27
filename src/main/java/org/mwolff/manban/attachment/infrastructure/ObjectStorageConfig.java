package org.mwolff.manban.attachment.infrastructure;

import io.minio.MinioClient;
import org.mwolff.manban.attachment.application.ObjectStorageProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stellt den S3-Client bereit (verbindet sich erst bei tatsächlicher Nutzung).
 *
 * <p>{@code io.minio} dient hier ausschließlich als <em>S3-Client-Bibliothek</em> — der Server
 * dahinter ist seit Plan #1222 (Issue #1226) SeaweedFS mit eingeschaltetem S3-Dienst, nicht MinIO.
 * Der Name der Bibliothek ist also kein Hinweis auf den laufenden Speicher; gesprochen wird das
 * S3-Protokoll, und der Endpunkt kommt aus {@code manban.storage.endpoint}.
 */
@Configuration
class ObjectStorageConfig {

  @Bean
  MinioClient minioClient(ObjectStorageProperties properties) {
    return MinioClient.builder()
        .endpoint(properties.endpoint())
        .credentials(properties.accessKey(), properties.secretKey())
        .build();
  }
}
