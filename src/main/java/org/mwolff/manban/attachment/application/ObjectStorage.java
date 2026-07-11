package org.mwolff.manban.attachment.application;

import java.io.InputStream;

/** Ausgehender Port zum Objektspeicher (Blobs). */
public interface ObjectStorage {

  void put(String objectKey, byte[] content, String contentType);

  InputStream get(String objectKey);

  void delete(String objectKey);
}
