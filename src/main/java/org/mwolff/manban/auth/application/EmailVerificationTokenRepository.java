package org.mwolff.manban.auth.application;

import org.mwolff.manban.auth.domain.EmailVerificationToken;

/** Ausgehender Port für die Persistenz von E-Mail-Verifikations-Tokens. */
public interface EmailVerificationTokenRepository extends SingleUseTokenRepository {

  EmailVerificationToken save(EmailVerificationToken token);
}
