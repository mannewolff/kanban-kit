package org.mwolff.manban.auth.application;

import java.time.Instant;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.EmailVerificationToken;
import org.mwolff.manban.common.SecureTokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Löst ein E-Mail-Verifikations-Token ein: prüft Gültigkeit, markiert die E-Mail des
 * Benutzers als bestätigt und verbraucht das Token (einmalig).
 */
@Service
public class VerifyEmailService {

    private final AppUserRepository users;
    private final EmailVerificationTokenRepository tokens;

    public VerifyEmailService(AppUserRepository users, EmailVerificationTokenRepository tokens) {
        this.users = users;
        this.tokens = tokens;
    }

    @Transactional
    public void verify(String plaintextToken) {
        Instant now = Instant.now();

        EmailVerificationToken token = tokens.findByTokenHash(SecureTokens.sha256Hex(plaintextToken))
                .orElseThrow(InvalidVerificationTokenException::new);

        if (token.isUsed() || token.isExpired(now)) {
            throw new InvalidVerificationTokenException();
        }

        AppUser user = users.findById(token.userId())
                .orElseThrow(InvalidVerificationTokenException::new);

        users.save(user.withEmailVerified(true));
        tokens.save(token.markUsed(now));
    }
}
