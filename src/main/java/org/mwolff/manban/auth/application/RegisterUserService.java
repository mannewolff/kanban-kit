package org.mwolff.manban.auth.application;

import java.time.Clock;
import java.util.Locale;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.EmailVerificationToken;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.SecureTokens;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registriert einen neuen Benutzer: legt ihn (unverifiziert) mit Argon2id-Passwort-Hash an, erzeugt
 * ein Verifikations-Token und stößt den Versand der Verifikations-E-Mail an.
 */
@Service
public class RegisterUserService {

  private final AppUserRepository users;
  private final EmailVerificationTokenRepository tokens;
  private final VerificationMailer mailer;
  private final PasswordEncoder passwordEncoder;
  private final AuthProperties properties;
  private final Clock clock;

  public RegisterUserService(
      AppUserRepository users,
      EmailVerificationTokenRepository tokens,
      VerificationMailer mailer,
      PasswordEncoder passwordEncoder,
      AuthProperties properties,
      Clock clock) {
    this.users = users;
    this.tokens = tokens;
    this.mailer = mailer;
    this.passwordEncoder = passwordEncoder;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional
  public AppUser register(String email, String rawPassword, String displayName) {
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    if (users.existsByEmail(normalizedEmail)) {
      throw new EmailAlreadyRegisteredException();
    }

    AppUser user =
        users.save(
            new AppUser(
                null,
                normalizedEmail,
                passwordEncoder.encode(rawPassword),
                displayName.trim(),
                false,
                PlatformRole.USER));

    String plaintext = SecureTokens.newToken();
    tokens.save(
        new EmailVerificationToken(
            null,
            user.id(),
            SecureTokens.sha256Hex(plaintext),
            clock.instant().plus(properties.verificationTtl()),
            null));

    String verificationUrl = properties.baseUrl() + "/api/auth/verify?token=" + plaintext;
    mailer.sendVerificationEmail(user.email(), verificationUrl);

    return user;
  }
}
