package org.mwolff.manban.entscheidungskette.domain;

/**
 * Die Einarbeitung einer Prüfrunde; sie gehört zur Runde mit derselben Nummer.
 *
 * @param runde die Nummer der Runde
 */
public record Einarbeitung(int runde) implements KitMeldung {}
