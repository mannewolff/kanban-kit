package org.mwolff.manban.auth.infrastructure.persistence;

import org.mwolff.manban.auth.application.EmailVerificationTokenRepository;
import org.mwolff.manban.auth.domain.EmailVerificationToken;
import org.springframework.stereotype.Component;

/** Adapter des {@link EmailVerificationTokenRepository}-Ports auf Spring Data JPA. */
@Component
class EmailVerificationTokenRepositoryAdapter
    extends AbstractAuthTokenRepositoryAdapter<EmailVerificationToken, EmailVerificationTokenEntity>
    implements EmailVerificationTokenRepository {

  EmailVerificationTokenRepositoryAdapter(EmailVerificationTokenJpaRepository jpa) {
    super(jpa);
  }

  @Override
  protected EmailVerificationTokenEntity toEntity(EmailVerificationToken t) {
    return new EmailVerificationTokenEntity(
        t.id(), t.userId(), t.tokenHash(), t.expiresAt(), t.usedAt());
  }

  @Override
  protected EmailVerificationToken toDomain(EmailVerificationTokenEntity e) {
    return new EmailVerificationToken(
        e.getId(), e.getUserId(), e.getTokenHash(), e.getExpiresAt(), e.getUsedAt());
  }
}
