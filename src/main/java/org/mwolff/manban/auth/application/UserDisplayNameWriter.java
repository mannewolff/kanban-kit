package org.mwolff.manban.auth.application;

/**
 * Schreibender Port für den Anzeigenamen eines Benutzers (Issue #460). Damit ändert ein fremdes
 * Modul den globalen Anzeigenamen, ohne die Aggregat-Wurzel {@code auth.domain.AppUser} selbst zu
 * laden und zu speichern.
 *
 * <p><strong>Sicherheitshinweis:</strong> Dieser Port prüft <strong>keine</strong> Rechte — weder
 * Plattform-Admin (das leistet {@link AdminService#changeDisplayName}) noch projektbezogen. Der
 * Aufrufer <em>muss</em> die Autorisierung bereits durchgeführt haben; verbindlich ist {@code
 * Permission.MEMBER_REMOVE}, wie in {@code MembershipService#changeMemberDisplayName} als erste
 * Anweisung geprüft. Eine eigene Prüfung hier würde die vorhandene Projekt-Rechtelogik verdoppeln
 * oder verschärfen. Wer den Port ohne vorgelagerte Prüfung aufruft, überschreibt den
 * <strong>globalen</strong> Anzeigenamen eines beliebigen Benutzers und umgeht damit sowohl {@link
 * AdminService#changeDisplayName} (Plattform-Admin) als auch {@code MeService#updateDisplayName}
 * (nur selbst).
 *
 * <p>Der Aufruferkreis ist deshalb nicht nur dokumentiert, sondern maschinell begrenzt: {@code
 * ArchitectureTest.USER_DISPLAY_NAME_WRITER_HAT_AUFRUFER_WHITELIST} lässt ausschließlich {@code
 * auth.application} (Port und Implementierung) sowie {@code project.application} (autorisierender
 * Aufrufer) zu. Ein weiterer Aufrufer ist damit eine bewusste Regeländerung, kein Versehen.
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
