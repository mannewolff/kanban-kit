package org.mwolff.manban.auth.infrastructure.persistence;

import org.mwolff.manban.auth.application.PasswordResetTokenRepository;
import org.mwolff.manban.auth.domain.PasswordResetToken;
import org.springframework.stereotype.Component;

/** Adapter des {@link PasswordResetTokenRepository}-Ports auf Spring Data JPA. */
@Component
class PasswordResetTokenRepositoryAdapter
    extends AbstractAuthTokenRepositoryAdapter<PasswordResetToken, PasswordResetTokenEntity>
    implements PasswordResetTokenRepository {

  PasswordResetTokenRepositoryAdapter(PasswordResetTokenJpaRepository jpa) {
    super(jpa);
  }

  @Override
  protected PasswordResetTokenEntity toEntity(PasswordResetToken t) {
    return new PasswordResetTokenEntity(
        t.id(), t.userId(), t.tokenHash(), t.expiresAt(), t.usedAt());
  }

  @Override
  protected PasswordResetToken toDomain(PasswordResetTokenEntity e) {
    return new PasswordResetToken(
        e.getId(), e.getUserId(), e.getTokenHash(), e.getExpiresAt(), e.getUsedAt());
  }
}
