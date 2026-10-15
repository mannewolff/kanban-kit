package org.mwolff.manban.card.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Ein Aufenthalt eines Arbeitspakets in einem Zustand auf einem Board (Plan #1765, E1): von {@code
 * enteredAt} bis {@code leftAt}. Solange er andauert, sind {@code leftAt} und {@code endedBy}
 * {@code null} (offener Aufenthalt).
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param cardId Karte
 * @param boardId Board, auf dem die Karte in dieser Zeit zählte
 * @param status Zustand; {@code null} heißt „unbekannt“ — nur eine Spanne aus dem Nachtrag (E7)
 * @param enteredAt Beginn des Aufenthalts
 * @param leftAt Ende des Aufenthalts; {@code null}, solange er offen ist
 * @param enteredKnown ob der Beginn der wirkliche Eintritt ist — {@code false} bei der Saat ab
 *     Migrationszeitpunkt und nach einer unbekannten Spanne (E8)
 * @param endedBy warum der Aufenthalt endete; {@code null}, solange er offen ist
 */
public record FlussStay(
    @Nullable Long id,
    long cardId,
    long boardId,
    @Nullable CardStatus status,
    Instant enteredAt,
    @Nullable Instant leftAt,
    boolean enteredKnown,
    @Nullable EndedBy endedBy) {

  /**
   * Warum ein Aufenthalt endete (E4). Der Konstantenname ist der gespeicherte Wert, die
   * Check-Bedingung {@code chk_card_flow_stay_ended_by} aus {@code V56} zählt genau diese Namen
   * auf.
   */
  public enum EndedBy {
    /** Der Zustand wechselte. */
    STATUS,
    /** Die Karte wechselte auf ein anderes Board. */
    BOARD,
    /** Die Karte verließ den Fluss: Papierkorb, Archiv oder Artwechsel. */
    EXIT
  }
}
