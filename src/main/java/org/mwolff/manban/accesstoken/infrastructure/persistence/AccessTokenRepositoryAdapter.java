package org.mwolff.manban.accesstoken.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.accesstoken.application.AccessTokenRepository;
import org.mwolff.manban.accesstoken.domain.AccessToken;
import org.springframework.stereotype.Component;

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
