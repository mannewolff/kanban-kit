package org.mwolff.manban.card.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Ein Label, das einer Karte hinzugefügt wurde (Issue #1754, Plan #1752 E2): welches ({@code
 * labelName}, Schnappschuss des Namens beim Setzen), wann ({@code createdAt}) und von wem.
 *
 * <p>Herkunft wie bei {@link CardActivity}: {@code origin} und {@code tokenName} sind
 * server-verifiziert, {@code agent} und {@code laufStart} sind Client-Selbstauskünfte. Geliefert
 * werden nur Ereignisse eines Laufs, darum ist {@code laufStart} hier immer gesetzt.
 */
public record CardLabelEvent(
    long cardId,
    String labelName,
    Instant createdAt,
    @Nullable CardActivityOrigin origin,
    @Nullable String tokenName,
    @Nullable String agent,
    Instant laufStart) {}
