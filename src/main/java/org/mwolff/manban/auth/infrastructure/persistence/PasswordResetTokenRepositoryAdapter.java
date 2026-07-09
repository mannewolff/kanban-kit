package org.mwolff.manban.auth.infrastructure.persistence;

import java.util.Optional;
import org.mwolff.manban.auth.application.PasswordResetTokenRepository;
import org.mwolff.manban.auth.domain.PasswordResetToken;
import org.springframework.stereotype.Component;

/** Adapter des {@link PasswordResetTokenRepository}-Ports auf Spring Data JPA. */
@Component
class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepository {

    private final PasswordResetTokenJpaRepository jpa;

    PasswordResetTokenRepositoryAdapter(PasswordResetTokenJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        return toDomain(jpa.save(toEntity(token)));
    }

    @Override
    public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(PasswordResetTokenRepositoryAdapter::toDomain);
    }

    private static PasswordResetTokenEntity toEntity(PasswordResetToken t) {
        return new PasswordResetTokenEntity(t.id(), t.userId(), t.tokenHash(), t.expiresAt(), t.usedAt());
    }

    private static PasswordResetToken toDomain(PasswordResetTokenEntity e) {
        return new PasswordResetToken(
                e.getId(), e.getUserId(), e.getTokenHash(), e.getExpiresAt(), e.getUsedAt());
    }
}
