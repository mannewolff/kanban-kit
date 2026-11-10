package org.mwolff.manban.entscheidungskette.domain;

import org.jspecify.annotations.Nullable;

/**
 * Das nachgetragene Ergebnis des Build-Diensts zu einer Übertragung.
 *
 * @param versuch Kennung des Versuchs
 * @param commit der übertragene Stand
 * @param buildDienst Ergebnis und Zeitpunkt des Build-Diensts
 * @param quelle Verweis auf den Lauf, wörtlich
 */
public record BuildNachtrag(
    @Nullable String versuch, @Nullable String commit, Angabe buildDienst, @Nullable String quelle)
    implements KitMeldung {}
