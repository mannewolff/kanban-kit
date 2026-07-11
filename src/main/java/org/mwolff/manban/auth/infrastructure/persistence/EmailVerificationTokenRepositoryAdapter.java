package org.mwolff.manban.auth.infrastructure.persistence;

import java.util.Optional;
import org.mwolff.manban.auth.application.EmailVerificationTokenRepository;
import org.mwolff.manban.auth.domain.EmailVerificationToken;
import org.springframework.stereotype.Component;

/** Adapter des {@link EmailVerificationTokenRepository}-Ports auf Spring Data JPA. */
@Component
class EmailVerificationTokenRepositoryAdapter implements EmailVerificationTokenRepository {

  private final EmailVerificationTokenJpaRepository jpa;

  EmailVerificationTokenRepositoryAdapter(EmailVerificationTokenJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public EmailVerificationToken save(EmailVerificationToken token) {
    return toDomain(jpa.save(toEntity(token)));
  }

  @Override
  public Optional<EmailVerificationToken> findByTokenHash(String tokenHash) {
    return jpa.findByTokenHash(tokenHash).map(EmailVerificationTokenRepositoryAdapter::toDomain);
  }

  private static EmailVerificationTokenEntity toEntity(EmailVerificationToken t) {
    return new EmailVerificationTokenEntity(
        t.id(), t.userId(), t.tokenHash(), t.expiresAt(), t.usedAt());
  }

  private static EmailVerificationToken toDomain(EmailVerificationTokenEntity e) {
    return new EmailVerificationToken(
        e.getId(), e.getUserId(), e.getTokenHash(), e.getExpiresAt(), e.getUsedAt());
  }
}
