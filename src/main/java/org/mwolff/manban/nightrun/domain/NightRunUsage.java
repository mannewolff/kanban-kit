package org.mwolff.manban.nightrun.domain;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Was ein Lauf oder ein Arbeitspaket verbraucht hat (Issue #944, Plan #943).
 *
 * <p>Ein eigener Typ statt acht loser Felder an zwei Records: Lauf und Arbeitspaket tragen dieselbe
 * Form, und wer sie einmal liest, liest sie überall gleich.
 *
 * <p><b>Jedes Feld darf fehlen.</b> {@code null} heißt „nicht gemessen" und nie Null — für Altläufe
 * sind die Werte nicht rekonstruierbar, und eine 0 behauptete, der Lauf habe nichts verbraucht.
 *
 * @param costUsd der gemeldete Betrag, nicht ein aus Mengen und Tarif gerechneter. Wer rechnete,
 *     änderte die Vergangenheit, sobald ein Preis sich ändert.
 * @param inputTokens verarbeitete Eingabemenge
 * @param outputTokens erzeugte Ausgabemenge
 * @param cachedInputTokens der Anteil der Eingabe, der aus dem Zwischenspeicher kam
 */
public record NightRunUsage(
    @Nullable BigDecimal costUsd,
    @Nullable Long inputTokens,
    @Nullable Long outputTokens,
    @Nullable Long cachedInputTokens) {}
