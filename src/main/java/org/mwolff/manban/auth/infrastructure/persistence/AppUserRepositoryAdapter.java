package org.mwolff.manban.auth.infrastructure.persistence;

import java.util.Optional;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.springframework.stereotype.Component;

/** Adapter, der den {@link AppUserRepository}-Port auf Spring Data JPA abbildet. */
@Component
class AppUserRepositoryAdapter implements AppUserRepository {

    private final AppUserJpaRepository jpa;

    AppUserRepositoryAdapter(AppUserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public AppUser save(AppUser user) {
        AppUserEntity saved = jpa.save(toEntity(user));
        return toDomain(saved);
    }

    @Override
    public Optional<AppUser> findByEmail(String email) {
        return jpa.findByEmail(email).map(AppUserRepositoryAdapter::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpa.existsByEmail(email);
    }

    private static AppUserEntity toEntity(AppUser u) {
        return new AppUserEntity(
                u.id(), u.email(), u.passwordHash(), u.displayName(), u.emailVerified(), u.platformRole());
    }

    private static AppUser toDomain(AppUserEntity e) {
        return new AppUser(
                e.getId(), e.getEmail(), e.getPasswordHash(), e.getDisplayName(),
                e.isEmailVerified(), e.getPlatformRole());
    }
}
