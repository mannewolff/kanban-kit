package org.mwolff.manban.comment.domain;

import java.time.Instant;

/**
 * Kommentar an einer Karte.
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param cardId zugehörige Karte
 * @param authorUserId Autor-Benutzer; {@code null} bei rein PAT-erzeugten Kommentaren
 * @param authorName Anzeigename des Autors
 * @param body Kommentartext
 * @param createdAt Erstellzeitpunkt
 * @param updatedAt letzte Änderung
 */
public record Comment(
    Long id,
    Long cardId,
    Long authorUserId,
    String authorName,
    String body,
    Instant createdAt,
    Instant updatedAt) {

  public Comment withBody(String newBody) {
    return new Comment(id, cardId, authorUserId, authorName, newBody, createdAt, updatedAt);
  }
}
