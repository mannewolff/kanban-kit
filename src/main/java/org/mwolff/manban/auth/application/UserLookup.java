package org.mwolff.manban.auth.application;

import java.util.Optional;

/**
 * Fachlicher Lese-Port des {@code auth}-Moduls für andere Module (Issue #460). Ersetzt den direkten
 * Zugriff auf {@link AppUserRepository} und {@code auth.domain.AppUser}: Aufrufer bekommen nur die
 * {@link UserSummary}, nicht die Aggregat-Wurzel.
 *
 * <p>Führt <strong>keine</strong> Rechteprüfung durch — reines Nachschlagen. Die Autorisierung
 * bleibt beim aufrufenden Modul.
 */
public interface UserLookup {

  /** Benutzer zu einer E-Mail-Adresse; leer, wenn keiner registriert ist. */
  Optional<UserSummary> findByEmail(String email);

  /** Benutzer zu einer technischen ID; leer, wenn unbekannt. */
  Optional<UserSummary> findById(long userId);
}
