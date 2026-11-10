package org.mwolff.manban.card.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Eine Zeile aus {@code card_label_event} mit ihrer ID (Issue #2075, Plan #2070 E4): Die
 * Benachrichtigung hält je {@code id} höchstens einen Anlass fest. Anders als {@link
 * CardLabelEvent} beschreibt sie auch Ereignisse ohne Lauf.
 *
 * @param id Zeilen-ID des Ereignisses
 * @param labelName Name des Labels beim Setzen
 * @param laufStart Laufkennung ({@code run_started_at}); {@code null}, wenn sich kein Lauf
 *     ausgewiesen hat
 */
public record LabelEreignisZeile(
    long id, long cardId, String labelName, Instant createdAt, @Nullable Instant laufStart) {}
