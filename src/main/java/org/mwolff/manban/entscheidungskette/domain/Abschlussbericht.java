package org.mwolff.manban.entscheidungskette.domain;

import org.jspecify.annotations.Nullable;

/**
 * Der Abschlussbericht einer Umsetzung, gelesen aus den festen Schlusszeilen.
 *
 * @param commit der Commit; {@code null} ohne Angabe
 * @param prueflauf Ergebnis und Zeitpunkt des deckenden Prüflaufs
 * @param umgesetztVon das Modell der Umsetzung; {@code null} ohne Angabe
 */
public record Abschlussbericht(
    @Nullable String commit, Angabe prueflauf, @Nullable String umgesetztVon)
    implements KitMeldung {}
