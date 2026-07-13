package org.mwolff.manban.auth.application;

import java.time.Clock;
import java.time.Instant;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.EmailVerificationToken;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.SecureTokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Löst ein E-Mail-Verifikations-Token ein: prüft Gültigkeit, markiert die E-Mail des Benutzers als
 * bestätigt und verbraucht das Token (einmalig). Wartet der Benutzer danach noch auf Admin-Freigabe
 * (Issue #0097), werden alle Plattform-Admins per Mail benachrichtigt (Issue #0098).
 */
@Service
public class VerifyEmailService {

  private final AppUserRepository users;
  private final EmailVerificationTokenRepository tokens;
  private final AdminNotificationMailer adminNotificationMailer;
  private final Clock clock;

  public VerifyEmailService(
      AppUserRepository users,
      EmailVerificationTokenRepository tokens,
      AdminNotificationMailer adminNotificationMailer,
      Clock clock) {
    this.users = users;
    this.tokens = tokens;
    this.adminNotificationMailer = adminNotificationMailer;
    this.clock = clock;
  }

  @Transactional
  public void verify(String plaintextToken) {
    Instant now = clock.instant();

    EmailVerificationToken token =
        tokens
            .findByTokenHash(SecureTokens.sha256Hex(plaintextToken))
            .orElseThrow(InvalidVerificationTokenException::new);

    if (token.isUsed() || token.isExpired(now)) {
      throw new InvalidVerificationTokenException();
    }

    AppUser user =
        users.findById(token.userId()).orElseThrow(InvalidVerificationTokenException::new);

    AppUser verified = users.save(user.withEmailVerified(true));
    tokens.save(token.markUsed(now));

    if (!verified.approved()) {
      notifyAdminsOfPendingApproval(verified);
    }
  }

  private void notifyAdminsOfPendingApproval(AppUser pendingUser) {
    for (AppUser admin : users.findByPlatformRole(PlatformRole.ADMIN)) {
      adminNotificationMailer.sendNewUserPendingApproval(
          admin.email(), pendingUser.email(), pendingUser.displayName());
    }
  }
}
