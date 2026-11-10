package org.mwolff.manban.project.application;

/**
 * Anwendungs-Event: ein Mitglied ist nicht mehr Admin oder Owner des Projekts, weil es herabgestuft
 * oder entfernt wurde (Issue #2104, Plan #2100 E4). Wird in der Transaktion der Änderung
 * publiziert, damit andere Module reagieren können, ohne dass {@code project} sie kennen muss.
 *
 * @param projectId das betroffene Projekt
 * @param userId das Mitglied, das die Rolle verloren hat
 */
public record MitgliedRolleVerlorenEvent(long projectId, long userId) {}
