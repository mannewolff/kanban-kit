package org.mwolff.manban.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plattform-Administration: Nutzer auflisten, Plattform-Rollen setzen und Registrierungen
 * freigeben. Alle Operationen erfordern, dass der Aufrufer selbst Plattform-Admin ist. Der letzte
 * Admin ist gegen Degradierung geschützt (kein Aussperren).
 */
@Service
public class AdminService {

  private final AppUserRepository users;
  private final Clock clock;

  public AdminService(AppUserRepository users, Clock clock) {
    this.users = users;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public boolean isPlatformAdmin(long userId) {
    return users.findById(userId).map(u -> u.platformRole() == PlatformRole.ADMIN).orElse(false);
  }

  @Transactional(readOnly = true)
  public List<UserView> listUsers(long actorUserId) {
    requirePlatformAdmin(actorUserId);
    return users.findAll().stream().map(AdminService::toView).toList();
  }

  @Transactional
  public UserView changePlatformRole(long actorUserId, long targetUserId, PlatformRole newRole) {
    requirePlatformAdmin(actorUserId);
    AppUser target = users.findById(targetUserId).orElseThrow(UserNotFoundException::new);

    // Letzten Admin nicht degradieren (Aussperr-Schutz).
    if (target.platformRole() == PlatformRole.ADMIN
        && newRole != PlatformRole.ADMIN
        && adminCount() <= 1) {
      throw new LastAdminException();
    }

    AppUser saved = users.save(target.withPlatformRole(newRole));
    return toView(saved);
  }

  /**
   * Gibt einen Benutzer frei. Idempotent: Ein bereits freigegebener Benutzer bleibt unverändert
   * (Zeitpunkt und freigebender Admin werden nicht überschrieben).
   */
  @Transactional
  public UserView approve(long actorUserId, long targetUserId) {
    requirePlatformAdmin(actorUserId);
    AppUser target = users.findById(targetUserId).orElseThrow(UserNotFoundException::new);
    if (target.approved()) {
      return toView(target);
    }
    AppUser saved = users.save(target.withApproved(clock.instant(), actorUserId));
    return toView(saved);
  }

  private void requirePlatformAdmin(long actorUserId) {
    if (!isPlatformAdmin(actorUserId)) {
      throw new AdminAccessDeniedException();
    }
  }

  private long adminCount() {
    return users.findAll().stream().filter(u -> u.platformRole() == PlatformRole.ADMIN).count();
  }

  private static UserView toView(AppUser u) {
    return new UserView(
        u.requireId(),
        u.email(),
        u.displayName(),
        u.platformRole(),
        u.emailVerified(),
        u.approvedAt());
  }

  /** Nutzerdarstellung für die Admin-Verwaltung. */
  public record UserView(
      Long id,
      String email,
      String displayName,
      PlatformRole platformRole,
      boolean emailVerified,
      @Nullable Instant approvedAt) {}
}
