package org.mwolff.manban.auth.application;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.auth.domain.AppUser;

/**
 * Ausgehender Port für die Persistenz von Benutzern. Die Anwendungs-/Domänenschicht spricht nur
 * gegen dieses Interface; die konkrete Umsetzung liegt in der Infrastruktur.
 */
public interface AppUserRepository {

  AppUser save(AppUser user);

  Optional<AppUser> findById(Long id);

  /** Alle Benutzer (für die Admin-Nutzerverwaltung). */
  List<AppUser> findAll();

  Optional<AppUser> findByEmail(String email);

  boolean existsByEmail(String email);
}
