package org.mwolff.manban.accesstoken.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.accesstoken.application.AccessTokenRepository;
import org.mwolff.manban.accesstoken.domain.AccessToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Adapter des {@link AccessTokenRepository}-Ports auf Spring Data JPA. */
@Component
class AccessTokenRepositoryAdapter implements AccessTokenRepository {

  private final KanbanAccessTokenJpaRepository jpa;

  AccessTokenRepositoryAdapter(KanbanAccessTokenJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public AccessToken save(AccessToken token) {
    return toDomain(jpa.save(toEntity(token)));
  }

  @Override
  public Optional<AccessToken> findById(long id) {
    return jpa.findById(id).map(AccessTokenRepositoryAdapter::toDomain);
  }

  @Override
  public List<AccessToken> findByUserId(long userId) {
    return jpa.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(AccessTokenRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public Optional<AccessToken> findByTokenHash(String tokenHash) {
    return jpa.findByTokenHash(tokenHash).map(AccessTokenRepositoryAdapter::toDomain);
  }

  /**
   * {@inheritDoc}
   *
   * <p>{@code REQUIRES_NEW} statt Mitlaufen in einer umschließenden Transaktion (Issue #997): Der
   * Stempel wird in einer eigenen Transaktion geschrieben und sofort committet. Der Zeilen-Lock
   * besteht damit für die Dauer eines {@code UPDATE}, nicht für die Dauer eines API-Aufrufs. Die
   * Auflösung selbst läuft ohne Transaktion, damit hier keine zweite Verbindung neben einer noch
   * gehaltenen angefordert wird (siehe {@code AccessTokenService#resolveBinding}).
   */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void touchLastUsedAtInOwnTransaction(long id, Instant when) {
    jpa.touchLastUsedAt(id, when);
  }

  @Override
  public void markRevoked(long id) {
    jpa.markRevoked(id);
  }

  private static KanbanAccessTokenEntity toEntity(AccessToken t) {
    return new KanbanAccessTokenEntity(t);
  }

  private static AccessToken toDomain(KanbanAccessTokenEntity e) {
    return new AccessToken(
        e.getId(),
        e.getUserId(),
        e.getProjectId(),
        e.getBoardId(),
        e.getName(),
        e.getTokenHash(),
        e.getDisplayName(),
        e.getCreatedAt(),
        e.getLastUsedAt(),
        e.isRevoked());
  }
}
