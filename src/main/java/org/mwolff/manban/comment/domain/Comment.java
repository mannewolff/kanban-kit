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
 * @param vonHandGeaendertAm letzte Änderung von Hand an einer Meldung des Kits (Issue #2109, Plan
 *     #2108 E4); {@code null}, wenn der Text vom Kit stammt oder nie eine Meldung war
 * @param vonHandKopfzeile erste nichtleere Zeile der Fassung des Kits vor der Änderung von Hand;
 *     {@code null} ohne Änderung von Hand oder wenn jene Fassung keine solche Zeile trug
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
    @Nullable String agent,
    @Nullable Instant vonHandGeaendertAm,
    @Nullable String vonHandKopfzeile)
    implements Identifiable {

  private static final String HERKUNFT_TOKEN = "TOKEN";

  /** Ein Kommentar ohne Änderung von Hand — die bisherige Parameterliste (Plan #2108 E4). */
  public Comment(
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
      @Nullable String agent) {
    this(
        id,
        cardId,
        authorUserId,
        authorName,
        body,
        createdAt,
        updatedAt,
        laufStart,
        origin,
        tokenName,
        agent,
        null,
        null);
  }

  /**
   * Neuer Text vom Schreiber mit der Laufkennung {@code newLaufStart}; sie ersetzt die alte. Die
   * Herkunft bleibt die des Anlegens.
   *
   * <p>Ändert nicht ein Token den Text einer mit Herkunft {@code TOKEN} angelegten Meldung, gilt
   * das als Änderung von Hand (Plan #2108 E4). Die Kopfzeile bleibt dabei die der Fassung des Kits,
   * auch bei jeder weiteren Änderung von Hand. Ersetzt ein Token den Text, stammt er wieder ganz
   * vom Kit und die Marke fällt weg.
   *
   * @param schreiberHerkunft Herkunft der Änderung, {@code null} = unbekannt und damit kein Token
   * @param jetzt Zeitpunkt der Änderung
   */
  public Comment withBody(
      String newBody,
      @Nullable Instant newLaufStart,
      @Nullable String schreiberHerkunft,
      Instant jetzt) {
    boolean vonHand = HERKUNFT_TOKEN.equals(origin) && !HERKUNFT_TOKEN.equals(schreiberHerkunft);
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
        agent,
        vonHand ? jetzt : null,
        vonHand ? kopfzeileDerKitFassung() : null);
  }

  private @Nullable String kopfzeileDerKitFassung() {
    if (vonHandKopfzeile != null) {
      return vonHandKopfzeile;
    }
    return body.lines().filter(zeile -> !zeile.isBlank()).findFirst().orElse(null);
  }
}
