package org.mwolff.manban.nightrun.domain;

import org.jspecify.annotations.Nullable;

/**
 * Was ein Arbeitspaket in einer Stufe der Kette gebraucht hat (Issue #1112, Plan #1110).
 *
 * <p>Ein Vorgang durchläuft die Stufen nacheinander; je Stufe fällt dieselbe Form von Messwerten an
 * wie am Paket selbst. Der Verbrauch steht deshalb als {@link NightRunUsage} und nicht als loses
 * Bündel von Spalten — dieselbe Begründung wie dort, und {@code plus} rechnet über Stufen hinweg
 * ohne zweite Regel.
 *
 * <p>Je Paket trägt jede Stufe höchstens einen Eintrag; die Datenbank hält das über den eindeutigen
 * Schlüssel {@code (night_run_item_id, stage)} fest.
 *
 * @param stage die Stufe, auf die sich die Messwerte beziehen
 * @param durationMs Wanduhr-Dauer der Stufe in Millisekunden; {@code null} heißt „nicht gemessen"
 * @param usage Verbrauch der Stufe; {@code null}, wenn nichts davon gemessen wurde
 */
public record NightRunItemStage(
    NightRunStage stage, @Nullable Long durationMs, @Nullable NightRunUsage usage) {}
