package org.mwolff.manban.auth.application;

/**
 * Schlanke Sicht auf einen Benutzer für modulfremde Aufrufer. Bewusst nur die Felder, die andere
 * Module fachlich brauchen (Zuordnung per E-Mail, Anzeigename, Freigabe-Status) — Passwort-Hash,
 * Plattform-Rolle und Sperr-/Verifikationszeitpunkte bleiben im {@code auth}-Modul.
 *
 * @param id technische ID des (stets persistierten) Benutzers
 * @param email eindeutige E-Mail-Adresse
 * @param displayName Anzeigename
 * @param approved ob der Benutzer freigegeben ist
 */
public record UserSummary(long id, String email, String displayName, boolean approved) {}
