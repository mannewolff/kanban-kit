package org.mwolff.manban.attachment.domain;

import java.time.Instant;

/**
 * Metadaten eines Karten-Anhangs. Der eigentliche Blob liegt im Objektspeicher (MinIO),
 * referenziert über {@code objectKey}; in der DB stehen nur Metadaten.
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param cardId zugehörige Karte
 * @param filename ursprünglicher Dateiname
 * @param contentType per Magic-Bytes erkannter Content-Type
 * @param size Größe in Bytes
 * @param objectKey Schlüssel des Objekts im Bucket
 * @param createdAt Upload-Zeitpunkt
 */
public record Attachment(
    Long id,
    Long cardId,
    String filename,
    String contentType,
    long size,
    String objectKey,
    Instant createdAt) {}
