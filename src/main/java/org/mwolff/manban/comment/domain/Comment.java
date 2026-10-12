package org.mwolff.manban.comment.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;

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
 * @param laufStart Laufkennung des letzten Schreibers aus dem Header {@code X-Night-Run} (Issue
 *     #1428, Plan #1423 E3); {@code null}, wenn er sich nicht ausgewiesen hat
 * @param origin Herkunft beim Anlegen, {@code SESSION} oder {@code TOKEN} (Issue #1620); {@code
 *     null} = unbekannt
 * @param tokenName Name des Projekt-Zugangs bei Herkunft {@code TOKEN}; sonst {@code null}
 * @param agent Modell-Selbstauskunft des Werkzeugs; {@code null}, wenn keine angegeben wurde
 */
public record Comment(
    @Nullable Long id,
    Long cardId,
    @Nullable Long authorUserId,
    String authorName,
    String body,
    Instant createdAt,
    Instant updatedAt,
    @Nullable Instant laufStart,
    @Nullable String origin,
    @Nullable String tokenName,
    @Nullable String agent)
    implements Identifiable {

  /**
   * Neuer Text vom Schreiber mit der Laufkennung {@code newLaufStart}; sie ersetzt die alte. Die
   * Herkunft bleibt die des Anlegens.
   */
  public Comment withBody(String newBody, @Nullable Instant newLaufStart) {
    return new Comment(
        id,
        cardId,
        authorUserId,
        authorName,
        newBody,
        createdAt,
        updatedAt,
        newLaufStart,
        origin,
        tokenName,
        agent);
  }
}
