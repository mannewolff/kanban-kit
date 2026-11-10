package org.mwolff.manban.entscheidungskette.domain;

/**
 * Ein Code-Review des Kits.
 *
 * @param funde seine Funde je Schwere
 */
public record CodeReview(Fundzahlen funde) implements KitMeldung {}
