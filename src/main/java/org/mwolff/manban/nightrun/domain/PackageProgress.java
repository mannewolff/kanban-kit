package org.mwolff.manban.nightrun.domain;

/**
 * Ein Arbeitspaket des Laufs mit seinem Zustand (Issue #1374, Plan #1372 E6).
 *
 * @param karte die Karte des Pakets
 * @param zustand sein Zustand
 */
public record PackageProgress(CardRef karte, PackageState zustand) {}
