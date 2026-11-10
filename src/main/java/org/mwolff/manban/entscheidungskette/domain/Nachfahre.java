package org.mwolff.manban.entscheidungskette.domain;

import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Ein Nachfahre am Fachplan oder Plan (Kriterium 3): nur ein Verweis mit Spalte und zuletzt
 * erreichtem Glied; die volle Kette steht an seiner eigenen Karte.
 *
 * @param zustand {@code NICHT_ERFASST} bei Papierkorb und anderem Projekt
 * @param verweis die Karte
 * @param spalte der Name ihrer Spalte
 * @param spaltenSchluessel der Schlüssel ihrer Spalte
 * @param zuletztErreicht das letzte erfasste Glied; {@code null}, wenn nicht erfasst
 * @param hinweise etwa „anderes Projekt“
 */
public record Nachfahre(
    Zustand zustand,
    Verweis verweis,
    @Nullable String spalte,
    @Nullable String spaltenSchluessel,
    @Nullable Gliedart zuletztErreicht,
    Set<Hinweis> hinweise) {}
