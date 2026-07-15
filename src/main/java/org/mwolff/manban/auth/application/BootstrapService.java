package org.mwolff.manban.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import org.mwolff.manban.auth.application.AdminService.UserView;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-Bootstrap: hebt auf einer frischen Instanz den ersten Nutzer zum Plattform-Admin. Aktiv
 * nur, solange kein Admin existiert (selbstheilend, kein Aussperren) und nur mit dem konfigurierten
 * Env-Token. Der aufrufende (eingeloggte) Nutzer wird elevatet.
 */
@Service
public class BootstrapService {

  private final AppUserRepository users;
  private final BootstrapProperties properties;
  private final Clock clock;

  public BootstrapService(AppUserRepository users, BootstrapProperties properties, Clock clock) {
    this.users = users;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional
  public UserView bootstrap(long userId, String token) {
    if (adminExists()) {
      throw new BootstrapUnavailableException();
    }
    String configured = properties.adminToken();
    if (configured == null || configured.isBlank() || !constantTimeEquals(configured, token)) {
      throw new InvalidBootstrapTokenException();
    }
    AppUser user = users.findById(userId).orElseThrow(UserNotFoundException::new);
    // Der erste Admin gibt sich beim Bootstrap zugleich selbst frei — sonst würde ihn der
    // Login-Gate (Issue #0097) bei der nächsten Anmeldung aussperren.
    AppUser elevated = user.withPlatformRole(PlatformRole.ADMIN);
    AppUser saved =
        users.save(
            elevated.approved()
                ? elevated
                : elevated.withApproved(clock.instant(), user.requireId()));
    return new UserView(
        saved.requireId(),
        saved.email(),
        saved.displayName(),
        saved.platformRole(),
        saved.emailVerified(),
        saved.approvedAt(),
        saved.disabled());
  }

  private boolean adminExists() {
    return users.findAll().stream().anyMatch(u -> u.platformRole() == PlatformRole.ADMIN);
  }

  private static boolean constantTimeEquals(String expected, String actual) {
    return actual != null
        && MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
  }
}
