package org.mwolff.manban.card.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Ein Label-Ereignis mit der Person, die das Label gesetzt hat (Issue #2110, Plan #2108 E5): die
 * Grundlage der Freigabe einer Nacht-Kette. Als Mensch gilt die Person nur bei Herkunft {@link
 * CardActivityOrigin#SESSION}; über ein Token gesetzt, erscheint der Zugang.
 *
 * @param id Zeilen-ID des Ereignisses
 * @param labelName Name des Labels beim Setzen
 * @param actorUserId wer gesetzt hat; {@code null} bei älteren Ereignissen und nach dem Löschen des
 *     Nutzers
 * @param origin serververifizierte Herkunft; {@code null} ohne authentifizierten Kontext
 * @param tokenName Name des Zugangs bei Herkunft {@code TOKEN}
 */
public record LabelEreignisMitPerson(
    long id,
    long cardId,
    String labelName,
    Instant createdAt,
    @Nullable Long actorUserId,
    @Nullable CardActivityOrigin origin,
    @Nullable String tokenName) {}
