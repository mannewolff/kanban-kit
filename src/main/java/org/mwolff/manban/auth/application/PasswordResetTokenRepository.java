package org.mwolff.manban.auth.application;

import org.mwolff.manban.auth.domain.PasswordResetToken;

/** Ausgehender Port für die Persistenz von Passwort-Reset-Tokens. */
public interface PasswordResetTokenRepository extends SingleUseTokenRepository {

  PasswordResetToken save(PasswordResetToken token);
}
