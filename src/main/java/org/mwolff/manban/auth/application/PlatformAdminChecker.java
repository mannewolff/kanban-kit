package org.mwolff.manban.auth.application;

import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Zentrale Prüfung „ist dieser Nutzer Plattform-Admin?". Als eigener injizierter Bean vermeidet
 * dies Self-Invocation transaktionaler Methoden innerhalb von {@link AdminService} und {@code
 * PermissionChecker} (Sonar {@code java:S6809}: ein Aufruf über {@code this} umgeht den
 * Spring-Transaktions-Proxy) und die vormals doppelt geführte Abfrage in beiden Klassen.
 */
@Component
public class PlatformAdminChecker {

  private final AppUserRepository users;

  public PlatformAdminChecker(AppUserRepository users) {
    this.users = users;
  }

  @Transactional(readOnly = true)
  public boolean isPlatformAdmin(long userId) {
    return users.findById(userId).map(u -> u.platformRole() == PlatformRole.ADMIN).orElse(false);
  }

  /**
   * Wie {@link #isPlatformAdmin(long)}, verlangt aber zusätzlich ein nicht gesperrtes Konto (Issue
   * #1438). Für Prüfungen, die vor dem {@code DisabledUserGuardFilter} laufen und einen gesperrten
   * Admin deshalb selbst aussieben müssen.
   */
  @Transactional(readOnly = true)
  public boolean isActivePlatformAdmin(long userId) {
    return users
        .findById(userId)
        .map(u -> u.platformRole() == PlatformRole.ADMIN && !u.disabled())
        .orElse(false);
  }
}
