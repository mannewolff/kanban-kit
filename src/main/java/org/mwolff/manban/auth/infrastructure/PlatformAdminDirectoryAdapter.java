package org.mwolff.manban.auth.infrastructure;

import java.util.Comparator;
import java.util.List;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.application.PlatformAdminDirectory;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Umsetzung von {@link PlatformAdminDirectory} über die vorhandene Rollenabfrage des
 * Benutzer-Repositorys (Issue #827).
 *
 * <p>Paketprivat wie {@code UserDirectoryService}: Fremde Module injizieren den Port, nie die
 * Umsetzung — sonst käme über den konkreten Typ doch wieder das Benutzer-Aggregat in Reichweite.
 */
@Component
class PlatformAdminDirectoryAdapter implements PlatformAdminDirectory {

  private final AppUserRepository users;

  PlatformAdminDirectoryAdapter(AppUserRepository users) {
    this.users = users;
  }

  @Override
  @Transactional(readOnly = true)
  public List<String> adminEmails() {
    return users.findByPlatformRole(PlatformRole.ADMIN).stream()
        .map(AppUser::email)
        // Sortiert in der JVM statt per ORDER BY: Die Reihenfolge ist eine Zusicherung des Ports
        // (siehe PlatformAdminDirectory#adminEmails) und gehört damit an die Stelle, die den
        // Vertrag erfüllt — nicht in eine Abfrage, die auch andere Aufrufer bedient.
        .sorted(Comparator.naturalOrder())
        .toList();
  }
}
