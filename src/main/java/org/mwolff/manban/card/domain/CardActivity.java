package org.mwolff.manban.card.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Ein Aktivitätseintrag einer Karte: wer ({@code actorUserId}, {@code null} wenn Nutzer gelöscht)
 * hat wann ({@code createdAt}) welche Art von Änderung ({@code type}) mit welcher Kurzbeschreibung
 * ({@code detail}) vorgenommen.
 */
public record CardActivity(
    @Nullable Long id,
    long cardId,
    @Nullable Long actorUserId,
    CardActivityType type,
    String detail,
    Instant createdAt) {}
