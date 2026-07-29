package org.mwolff.manban.attachment.application;

import java.io.InputStream;
import java.util.List;

/** Ausgehender Port zum Objektspeicher (Blobs). */
public interface ObjectStorage {

  void put(String objectKey, byte[] content, String contentType);

  InputStream get(String objectKey);

  /** Entfernt das Objekt; idempotent — ein nicht (mehr) existierendes Objekt ist kein Fehler. */
  void delete(String objectKey);

  /** Alle Object-Keys des Buckets — für den Abgleich mit den Metadaten (Issue #503). */
  List<String> listKeys();
}
