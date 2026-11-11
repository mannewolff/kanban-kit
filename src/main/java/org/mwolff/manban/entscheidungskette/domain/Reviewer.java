package org.mwolff.manban.entscheidungskette.domain;

import org.jspecify.annotations.Nullable;

/**
 * Ein Prüfer einer Prüfrunde aus {@code ### Reviewer <n>: <rolle>, <modell>}.
 *
 * @param nummer seine Nummer in der Runde
 * @param rolle seine Rolle; {@code null}, wenn der Kopf keine nennt
 * @param modell sein Modell; {@code null}, wenn der Kopf keines nennt
 * @param funde seine Funde je Schwere
 */
public record Reviewer(
    int nummer, @Nullable String rolle, @Nullable String modell, Fundzahlen funde) {}
