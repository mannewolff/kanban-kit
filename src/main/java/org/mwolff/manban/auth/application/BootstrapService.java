package org.mwolff.manban.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.mwolff.manban.auth.application.AdminService.UserView;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-Bootstrap: hebt auf einer frischen Instanz den ersten Nutzer zum Plattform-Admin.
 * Aktiv nur, solange kein Admin existiert (selbstheilend, kein Aussperren) und nur mit dem
 * konfigurierten Env-Token. Der aufrufende (eingeloggte) Nutzer wird elevatet.
 */
@Service
public class BootstrapService {

    private final AppUserRepository users;
    private final BootstrapProperties properties;

    public BootstrapService(AppUserRepository users, BootstrapProperties properties) {
        this.users = users;
        this.properties = properties;
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
        AppUser saved = users.save(user.withPlatformRole(PlatformRole.ADMIN));
        return new UserView(saved.id(), saved.email(), saved.displayName(), saved.platformRole(), saved.emailVerified());
    }

    private boolean adminExists() {
        return users.findAll().stream().anyMatch(u -> u.platformRole() == PlatformRole.ADMIN);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
