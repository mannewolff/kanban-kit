package org.mwolff.manban.accesstoken.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.accesstoken.application.AccessTokenRepository;
import org.mwolff.manban.accesstoken.domain.AccessToken;
import org.mwolff.manban.accesstoken.domain.AccessTokenArt;
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

  @Override
  public Optional<AccessToken> findWirksamenAppZugang(long boardId) {
    return jpa.findByBoardIdAndArtAndRevokedFalse(boardId, AccessTokenArt.APP)
        .map(AccessTokenRepositoryAdapter::toDomain);
  }

  @Override
  public List<AccessToken> findWirksameAppZugaenge(Collection<Long> boardIds) {
    return jpa.findByBoardIdInAndArtAndRevokedFalse(boardIds, AccessTokenArt.APP).stream()
        .map(AccessTokenRepositoryAdapter::toDomain)
        .toList();
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

  @Override
  public boolean markRevokedIfActive(long id) {
    return jpa.markRevokedIfActive(id) > 0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>{@code REQUIRES_NEW} aus demselben Grund wie beim Stempel (Issue #997): Die Auflösung läuft
   * ohne Transaktion, und der Widerruf committet sofort.
   */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markRevokedInOwnTransaction(long id) {
    jpa.markRevoked(id);
  }

  /**
   * {@inheritDoc}
   *
   * <p>{@code MANDATORY}: Die Sperre gilt bis zum Ende der Transaktion; ohne eine liefe sie im
   * Autocommit und wäre sofort wieder frei.
   */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void sperreAppZugangAusgabe(long userId) {
    jpa.sperreAppZugangAusgabe(userId);
  }

  @Override
  public void markAppZugaengeRevoked(long userId) {
    jpa.markRevokedByUser(userId, AccessTokenArt.APP);
  }

  @Override
  public void markAppZugaengeRevoked(long userId, long projectId) {
    jpa.markRevokedByUserAndProject(userId, projectId, AccessTokenArt.APP);
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
        e.isRevoked(),
        e.getArt());
  }
}
