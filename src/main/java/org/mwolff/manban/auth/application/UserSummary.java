package org.mwolff.manban.auth.application;

/**
 * Schlanke Sicht auf einen Benutzer für modulfremde Aufrufer. Bewusst nur die Felder, die andere
 * Module fachlich brauchen (Zuordnung per E-Mail, Anzeigename, Freigabe-Status) — Passwort-Hash,
 * Plattform-Rolle und Sperr-/Verifikationszeitpunkte bleiben im {@code auth}-Modul.
 *
 * @param id technische ID des (stets persistierten) Benutzers
 * @param email eindeutige E-Mail-Adresse
 * @param displayName Anzeigename
 * @param approved ob der Benutzer mitwirken darf — die Freigabe durch einen Plattform-Admin, oder
 *     die Plattform-Rolle ADMIN selbst (die keine Fremdfreigabe braucht, Issue #556). Bewusst die
 *     fachliche Antwort statt des rohen Freigabe-Zeitstempels: Für den echten Zeitpunkt gibt es die
 *     Admin-Verwaltung im auth-Modul.
 */
public record UserSummary(long id, String email, String displayName, boolean approved) {}
