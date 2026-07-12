package org.mwolff.manban.auth.application;

import java.time.Clock;
import java.util.Locale;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PasswordResetToken;
import org.mwolff.manban.common.SecureTokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Startet einen Passwort-Reset: existiert der Benutzer, wird ein Reset-Token erzeugt und per E-Mail
 * zugestellt. Existiert er nicht, passiert nichts — nach außen ist der Ausgang identisch (keine
 * User-Enumeration).
 */
@Service
public class RequestPasswordResetService {

  private final AppUserRepository users;
  private final PasswordResetTokenRepository tokens;
  private final PasswordResetMailer mailer;
  private final AuthProperties properties;
  private final Clock clock;

  public RequestPasswordResetService(
      AppUserRepository users,
      PasswordResetTokenRepository tokens,
      PasswordResetMailer mailer,
      AuthProperties properties,
      Clock clock) {
    this.users = users;
    this.tokens = tokens;
    this.mailer = mailer;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional
  public void requestReset(String email) {
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    users.findByEmail(normalizedEmail).ifPresent(this::issueToken);
  }

  private void issueToken(AppUser user) {
    String plaintext = SecureTokens.newToken();
    tokens.save(
        new PasswordResetToken(
            null,
            user.requireId(),
            SecureTokens.sha256Hex(plaintext),
            clock.instant().plus(properties.resetTtl()),
            null));
    String resetUrl = properties.baseUrl() + "/reset?token=" + plaintext;
    mailer.sendPasswordResetEmail(user.email(), resetUrl);
  }
}
