package org.mwolff.manban.comment.domain;

import java.time.Instant;

/**
 * Die jüngste gemeldete Vorschau eines Projekts, wie die Auswertung der Freigaben sie braucht
 * (Issue #2210, Plan #2206 E11).
 *
 * @param gemeldetAm wann die Vorschau gemeldet wurde
 * @param revision der Stand, den sie zeigt
 */
public record Vorschau(Instant gemeldetAm, String revision) {}
