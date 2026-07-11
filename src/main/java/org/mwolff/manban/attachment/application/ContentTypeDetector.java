package org.mwolff.manban.attachment.application;

/** Ausgehender Port: Content-Type aus dem Dateiinhalt (Magic-Bytes) bestimmen. */
public interface ContentTypeDetector {

  String detect(byte[] content, String filename);
}
