package org.mwolff.manban.attachment.infrastructure;

import io.minio.MinioClient;
import org.mwolff.manban.attachment.application.ObjectStorageProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Stellt den MinIO-Client bereit (verbindet sich erst bei tatsächlicher Nutzung). */
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
