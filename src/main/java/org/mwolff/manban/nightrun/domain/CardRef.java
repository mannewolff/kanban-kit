package org.mwolff.manban.nightrun.domain;

/**
 * Verweis auf eine Karte im Fortschritt eines Laufs (Issue #1374, Plan #1372) — genug, um sie zu
 * benennen und zu öffnen.
 *
 * @param number Kartennummer im Projekt
 * @param title Titel der Karte
 * @param boardId Board, auf dem die Karte liegt
 */
public record CardRef(int number, String title, long boardId) {}
