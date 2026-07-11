package org.mwolff.manban.auth.application;

import java.time.Clock;
import java.time.Instant;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PasswordResetToken;
import org.mwolff.manban.common.SecureTokens;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Setzt das Passwort anhand eines gültigen Reset-Tokens neu und verbraucht das Token (einmalig).
 * Ungültige, abgelaufene oder bereits genutzte Tokens werden abgelehnt.
 */
@Service
public class ResetPasswordService {

  private final AppUserRepository users;
  private final PasswordResetTokenRepository tokens;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public ResetPasswordService(
      AppUserRepository users,
      PasswordResetTokenRepository tokens,
      PasswordEncoder passwordEncoder,
      Clock clock) {
    this.users = users;
    this.tokens = tokens;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  @Transactional
  public void reset(String plaintextToken, String newRawPassword) {
    Instant now = clock.instant();

    PasswordResetToken token =
        tokens
            .findByTokenHash(SecureTokens.sha256Hex(plaintextToken))
            .orElseThrow(InvalidResetTokenException::new);

    if (token.isUsed() || token.isExpired(now)) {
      throw new InvalidResetTokenException();
    }

    AppUser user = users.findById(token.userId()).orElseThrow(InvalidResetTokenException::new);

    users.save(user.withPasswordHash(passwordEncoder.encode(newRawPassword)));
    tokens.save(token.markUsed(now));
  }
}
