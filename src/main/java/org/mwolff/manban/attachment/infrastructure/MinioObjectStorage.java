package org.mwolff.manban.attachment.infrastructure;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.mwolff.manban.attachment.application.ObjectStorage;
import org.mwolff.manban.attachment.application.ObjectStorageProperties;
import org.springframework.stereotype.Component;

/**
 * {@link ObjectStorage} auf Basis von MinIO. Der Ziel-Bucket wird faul beim ersten Zugriff angelegt
 * (nicht beim Start), sodass Kontexte ohne Anhang-Nutzung keinen Objektspeicher benötigen.
 */
// PMD.AvoidCatchingGenericException: Der MinIO-Client deklariert bei jedem Aufruf ein breites
// Bündel geprüfter Ausnahmen (IO, Krypto, Server, XML-Parsing). Sie werden hier bewusst gebündelt
// als Infrastruktur-Fehler in ObjectStorageException gekapselt (§6.5: kein stilles Schlucken).
@SuppressWarnings("PMD.AvoidCatchingGenericException")
@Component
class MinioObjectStorage implements ObjectStorage {

  private final MinioClient client;
  private final String bucket;
  private volatile boolean bucketReady;

  MinioObjectStorage(MinioClient client, ObjectStorageProperties properties) {
    this.client = client;
    this.bucket = properties.bucket();
  }

  @Override
  public void put(String objectKey, byte[] content, String contentType) {
    ensureBucket();
    try {
      client.putObject(
          PutObjectArgs.builder().bucket(bucket).object(objectKey).stream(
                  new ByteArrayInputStream(content), content.length, -1)
              .contentType(contentType)
              .build());
    } catch (Exception e) {
      throw new ObjectStorageException("Upload fehlgeschlagen", e);
    }
  }

  @Override
  public InputStream get(String objectKey) {
    try {
      return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
    } catch (Exception e) {
      throw new ObjectStorageException("Download fehlgeschlagen", e);
    }
  }

  @Override
  public void delete(String objectKey) {
    try {
      client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
    } catch (Exception e) {
      throw new ObjectStorageException("Löschen fehlgeschlagen", e);
    }
  }

  private void ensureBucket() {
    if (bucketReady) {
      return;
    }
    synchronized (this) {
      if (bucketReady) {
        return;
      }
      try {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
          client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        bucketReady = true;
      } catch (Exception e) {
        throw new ObjectStorageException("Bucket-Initialisierung fehlgeschlagen", e);
      }
    }
  }

  static class ObjectStorageException extends RuntimeException {
    ObjectStorageException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
