package org.mwolff.manban.card.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Ein Spaltenaufenthalt einer Karte: von {@code enteredAt} bis {@code leftAt}. Solange die Karte in
 * der Spalte liegt, ist {@code leftAt}/{@code durationSeconds} {@code null} (offene Zeile). Beim
 * Verlassen wird {@code durationSeconds} als Sekunden zwischen Eintritt und Austritt festgehalten.
 * {@code columnName} ist ein Snapshot zum Eintrittszeitpunkt; {@code columnId} kann {@code null}
 * werden, wenn die Spalte später gelöscht wird.
 */
public record CardColumnTransition(
    @Nullable Long id,
    long cardId,
    @Nullable Long columnId,
    String columnName,
    Instant enteredAt,
    @Nullable Instant leftAt,
    @Nullable Long durationSeconds) {}
