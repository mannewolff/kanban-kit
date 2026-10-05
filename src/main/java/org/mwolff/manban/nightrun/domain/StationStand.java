package org.mwolff.manban.nightrun.domain;

import org.jspecify.annotations.Nullable;

/**
 * Stand einer Station der Stufenleiste (Issue #1451, Plan #1447 E5) — Zustand und Text fertig für
 * die Oberfläche.
 *
 * @param station die Station
 * @param zustand ihr Zustand
 * @param text der angezeigte Text, etwa {@code läuft (2 Prüfer)} oder {@code Ziel erreicht}
 * @param grund der Grund aus dem Laufstand, wörtlich, bei {@link StationsZustand#WARTET} und {@link
 *     StationsZustand#ABGEBROCHEN}; {@code null} sonst und ohne Grund
 */
public record StationStand(
    ProgressStage station, StationsZustand zustand, String text, @Nullable String grund) {}
