package org.mwolff.manban.accesstoken.application;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.accesstoken.domain.AccessToken;

/** Ausgehender Port für die Persistenz von API-Zugriffstokens. */
public interface AccessTokenRepository {

  AccessToken save(AccessToken token);

  Optional<AccessToken> findById(long id);

  List<AccessToken> findByUserId(long userId);

  Optional<AccessToken> findByTokenHash(String tokenHash);
}
