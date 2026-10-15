package org.mwolff.manban.card.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Eine Bewegung, die ein Lauf an einer Karte auslöste, mit dem Status davor (Issue #1755, Plan
 * #1752 E1, E16).
 *
 * @param cardId Karte der Bewegung
 * @param type Art der Aktivität, {@code MOVED} oder {@code STATUS_CHANGED}
 * @param createdAt Zeitpunkt der Bewegung
 * @param laufStart Laufkennung des Laufs, der sie auslöste
 * @param statusAfter Status der Karte nach der Bewegung
 * @param statusVorher Status nach der unmittelbar vorangehenden Aktivität mit bekanntem Status,
 *     gleich welcher Herkunft und wie alt; {@code null}, wenn es keine gibt
 */
public record LaufBewegung(
    long cardId,
    CardActivityType type,
    Instant createdAt,
    Instant laufStart,
    CardStatus statusAfter,
    @Nullable CardStatus statusVorher) {}
