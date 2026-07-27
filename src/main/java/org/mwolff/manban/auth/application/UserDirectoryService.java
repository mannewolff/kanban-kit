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
 */
@Service
public class UserDirectoryService implements UserLookup, UserDisplayNameWriter {

  private final AppUserRepository users;

  public UserDirectoryService(AppUserRepository users) {
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
  public UserSummary updateDisplayName(long userId, String displayName) {
    AppUser user = users.findById(userId).orElseThrow(UserNotFoundException::new);
    return toSummary(users.save(user.withDisplayName(displayName.trim())));
  }

  private static UserSummary toSummary(AppUser user) {
    return new UserSummary(user.requireId(), user.email(), user.displayName(), user.approved());
  }
}
