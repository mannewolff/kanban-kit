package org.mwolff.manban.auth.application;

import java.util.Optional;
import org.mwolff.manban.auth.domain.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementierung der modulfremden Benutzer-Ports (Issue #460): Sie ist die einzige Stelle, an der
 * ein Zugriff von außen auf {@link AppUserRepository} und das Benutzer-Aggregat landet.
 *
 * <p>Beide Ports sind bewusst rechteprüfungsfrei (siehe {@link UserDisplayNameWriter}); die
 * Autorisierung liegt beim aufrufenden Modul.
 *
 * <p>Die Klasse selbst ist paketprivat (#472): Sie vereint als einzige beide Ports und wäre als
 * öffentlicher Typ von außen injizierbar — womit die Lese-/Schreib-Trennung, die der Zweck des
 * Umbaus ist, mit einer einzigen Feld-Deklaration umgangen wäre. Fremde Module injizieren {@link
 * UserLookup} oder {@link UserDisplayNameWriter}, nie beides in einem Typ.
 */
@Service
class UserDirectoryService implements UserLookup, UserDisplayNameWriter {

  private final AppUserRepository users;

  UserDirectoryService(AppUserRepository users) {
    this.users = users;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserSummary> findByEmail(String email) {
    return users.findByEmail(email).map(UserDirectoryService::toSummary);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserSummary> findById(long userId) {
    return users.findById(userId).map(UserDirectoryService::toSummary);
  }

  @Override
  @Transactional
  public Optional<UserSummary> updateDisplayName(long userId, String displayName) {
    return users
        .findById(userId)
        .map(user -> toSummary(users.save(user.withDisplayName(displayName.trim()))));
  }

  private static UserSummary toSummary(AppUser user) {
    return new UserSummary(user.requireId(), user.email(), user.displayName(), user.approved());
  }
}
