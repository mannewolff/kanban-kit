package org.mwolff.manban.comment.domain;

import java.time.Instant;

/**
 * Ein Kommentar, dessen letzter Schreiber ein Lauf war (Issue #1755, Plan #1752 E5) — nur Karte und
 * Laufkennung, ohne Text.
 *
 * @param cardId Karte, an der der Kommentar steht
 * @param laufStart Laufkennung des letzten Schreibers
 */
public record LaufKommentar(long cardId, Instant laufStart) {}
