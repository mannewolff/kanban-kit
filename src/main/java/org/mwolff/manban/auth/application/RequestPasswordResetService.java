package org.mwolff.manban.auth.application;

import java.time.Instant;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PasswordResetToken;
import org.mwolff.manban.common.SecureTokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Startet einen Passwort-Reset: existiert der Benutzer, wird ein Reset-Token erzeugt
 * und per E-Mail zugestellt. Existiert er nicht, passiert nichts — nach außen ist der
 * Ausgang identisch (keine User-Enumeration).
 */
@Service
public class RequestPasswordResetService {

    private final AppUserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordResetMailer mailer;
    private final AuthProperties properties;

    public RequestPasswordResetService(AppUserRepository users, PasswordResetTokenRepository tokens,
                                       PasswordResetMailer mailer, AuthProperties properties) {
        this.users = users;
        this.tokens = tokens;
        this.mailer = mailer;
        this.properties = properties;
    }

    @Transactional
    public void requestReset(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        users.findByEmail(normalizedEmail).ifPresent(this::issueToken);
    }

    private void issueToken(AppUser user) {
        String plaintext = SecureTokens.newToken();
        tokens.save(new PasswordResetToken(
                null,
                user.id(),
                SecureTokens.sha256Hex(plaintext),
                Instant.now().plus(properties.resetTtl()),
                null));
        String resetUrl = properties.baseUrl() + "/api/auth/reset?token=" + plaintext;
        mailer.sendPasswordResetEmail(user.email(), resetUrl);
    }
}
