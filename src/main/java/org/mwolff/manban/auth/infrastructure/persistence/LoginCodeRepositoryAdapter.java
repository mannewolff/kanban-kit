package org.mwolff.manban.auth.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import org.mwolff.manban.auth.application.LoginCodeRepository;
import org.mwolff.manban.auth.domain.LoginCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter des {@link LoginCodeRepository}-Ports auf Spring Data JPA. Die schreibenden Methoden
 * laufen in einer Transaktion, die eine schon offene des Aufrufers mitnimmt — die nativen {@code
 * UPDATE … RETURNING} sind für Spring Data Lese-Abfragen und bekämen sonst keine schreibende.
 */
@Component
class LoginCodeRepositoryAdapter implements LoginCodeRepository {

  private final LoginCodeJpaRepository jpa;

  LoginCodeRepositoryAdapter(LoginCodeJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public LoginCode save(LoginCode code) {
    return jpa.save(new LoginCodeEntity(code)).toDomain();
  }

  @Override
  public long countSince(String emailHash, Instant since) {
    return jpa.countSince(emailHash, since);
  }

  @Override
  @Transactional
  public int supersedeOpen(String emailHash, Instant now) {
    return jpa.supersedeOpen(emailHash, now);
  }

  @Override
  @Transactional
  public Optional<Long> consumeByCode(
      String emailHash, String codeHmac, Instant now, int maxAttempts) {
    return jpa.consumeByCode(emailHash, codeHmac, now, maxAttempts);
  }

  @Override
  @Transactional
  public Optional<Long> consumeByLink(String linkHash, Instant now, int maxAttempts) {
    return jpa.consumeByLink(linkHash, now, maxAttempts);
  }

  @Override
  @Transactional
  public int recordFailure(String emailHash, Instant now, int maxAttempts) {
    return jpa.recordFailure(emailHash, now, maxAttempts);
  }

  @Override
  @Transactional
  public int deleteOlderThan(Instant cutoff) {
    return jpa.deleteOlderThan(cutoff);
  }
}
