package org.mwolff.manban.card.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Ein Aktivitätseintrag einer Karte: wer ({@code actorUserId}, {@code null} wenn Nutzer gelöscht)
 * hat wann ({@code createdAt}) welche Art von Änderung ({@code type}) mit welcher Kurzbeschreibung
 * ({@code detail}) vorgenommen.
 *
 * <p>Herkunft (Issue #517): {@code origin} und {@code tokenName} sind server-verifiziert (Session-
 * oder PAT-Authentifizierung bzw. Token-Bindung), {@code agent} ist eine Client-Selbstauskunft
 * (Header {@code X-Agent-Model}). Alt-Einträge vor Einführung tragen in allen drei Feldern {@code
 * null} — die Herkunft ist für die Vergangenheit nicht rekonstruierbar.
 */
public record CardActivity(
    @Nullable Long id,
    long cardId,
    @Nullable Long actorUserId,
    CardActivityType type,
    String detail,
    Instant createdAt,
    @Nullable CardActivityOrigin origin,
    @Nullable String tokenName,
    @Nullable String agent) {}
