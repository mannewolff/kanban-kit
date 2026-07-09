package org.mwolff.manban.auth.application;

import java.util.Optional;
import org.mwolff.manban.auth.domain.PasswordResetToken;

/** Ausgehender Port für die Persistenz von Passwort-Reset-Tokens. */
public interface PasswordResetTokenRepository {

    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
