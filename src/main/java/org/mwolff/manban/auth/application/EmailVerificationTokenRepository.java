package org.mwolff.manban.auth.application;

import java.util.Optional;
import org.mwolff.manban.auth.domain.EmailVerificationToken;

/** Ausgehender Port für die Persistenz von E-Mail-Verifikations-Tokens. */
public interface EmailVerificationTokenRepository {

  EmailVerificationToken save(EmailVerificationToken token);

  Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
}
