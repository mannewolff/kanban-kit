package org.mwolff.manban.auth.application;

import java.util.List;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plattform-Administration: Nutzer auflisten und Plattform-Rollen setzen. Alle Operationen
 * erfordern, dass der Aufrufer selbst Plattform-Admin ist. Der letzte Admin ist gegen
 * Degradierung geschützt (kein Aussperren).
 */
@Service
public class AdminService {

    private final AppUserRepository users;

    public AdminService(AppUserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public boolean isPlatformAdmin(long userId) {
        return users.findById(userId).map(u -> u.platformRole() == PlatformRole.ADMIN).orElse(false);
    }

    @Transactional(readOnly = true)
    public List<UserView> listUsers(long actorUserId) {
        requirePlatformAdmin(actorUserId);
        return users.findAll().stream()
                .map(u -> new UserView(u.id(), u.email(), u.displayName(), u.platformRole(), u.emailVerified()))
                .toList();
    }

    @Transactional
    public UserView changePlatformRole(long actorUserId, long targetUserId, PlatformRole newRole) {
        requirePlatformAdmin(actorUserId);
        AppUser target = users.findById(targetUserId).orElseThrow(UserNotFoundException::new);

        // Letzten Admin nicht degradieren (Aussperr-Schutz).
        if (target.platformRole() == PlatformRole.ADMIN && newRole != PlatformRole.ADMIN && adminCount() <= 1) {
            throw new LastAdminException();
        }

        AppUser saved = users.save(target.withPlatformRole(newRole));
        return new UserView(saved.id(), saved.email(), saved.displayName(), saved.platformRole(), saved.emailVerified());
    }

    private void requirePlatformAdmin(long actorUserId) {
        if (!isPlatformAdmin(actorUserId)) {
            throw new AdminAccessDeniedException();
        }
    }

    private long adminCount() {
        return users.findAll().stream().filter(u -> u.platformRole() == PlatformRole.ADMIN).count();
    }

    /** Nutzerdarstellung für die Admin-Verwaltung. */
    public record UserView(Long id, String email, String displayName, PlatformRole platformRole, boolean emailVerified) {
    }
}
