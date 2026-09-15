package org.mwolff.manban.accesstoken.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.accesstoken.domain.AccessToken;

/**
 * Ausgehender Port für die Persistenz von API-Zugriffstokens.
 *
 * <p>{@link #save} trägt ausschließlich das Anlegen. Nutzung und Widerruf haben je eine eigene,
 * spaltenscharfe Operation, damit keins von beiden das andere überschreibt (Issue #878).
 */
public interface AccessTokenRepository {

  AccessToken save(AccessToken token);

  Optional<AccessToken> findById(long id);

  List<AccessToken> findByUserId(long userId);

  Optional<AccessToken> findByTokenHash(String tokenHash);

  /**
   * Stempelt die Nutzung, sofern das Token nicht widerrufen ist — schreibt nur {@code lastUsedAt}.
   */
  void touchLastUsedAt(long id, Instant when);

  /** Widerruft das Token — schreibt nur {@code revoked}; idempotent. */
  void markRevoked(long id);
}
