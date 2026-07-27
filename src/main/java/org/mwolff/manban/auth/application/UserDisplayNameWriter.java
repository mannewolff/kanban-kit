package org.mwolff.manban.auth.application;

/**
 * Schreibender Port für den Anzeigenamen eines Benutzers (Issue #460). Damit ändert ein fremdes
 * Modul den globalen Anzeigenamen, ohne die Aggregat-Wurzel {@code auth.domain.AppUser} selbst zu
 * laden und zu speichern.
 *
 * <p><strong>Sicherheitshinweis:</strong> Dieser Port prüft bewusst <strong>keine</strong> Rechte —
 * weder Plattform-Admin (das leistet {@link AdminService#changeDisplayName}) noch projektbezogen.
 * Der Aufrufer muss die Autorisierung bereits durchgeführt haben (z. B. {@code
 * MembershipService.changeMemberDisplayName} über {@code Permission.MEMBER_REMOVE}). Eine eigene
 * Prüfung hier würde die vorhandene Projekt-Rechtelogik verdoppeln oder verschärfen.
 */
@FunctionalInterface
public interface UserDisplayNameWriter {

  /**
   * Setzt den Anzeigenamen (getrimmt) und liefert den aktualisierten Benutzer.
   *
   * @throws UserNotFoundException wenn zur ID kein Benutzer existiert
   */
  UserSummary updateDisplayName(long userId, String displayName);
}
