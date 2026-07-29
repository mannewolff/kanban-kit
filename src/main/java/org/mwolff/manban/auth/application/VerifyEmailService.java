package org.mwolff.manban.auth.application;

import java.time.Clock;
import java.time.Instant;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.SecureTokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Löst ein E-Mail-Verifikations-Token ein: verbraucht das Token (einmalig) und markiert die E-Mail
 * des Benutzers als bestätigt. Wartet der Benutzer danach noch auf Admin-Freigabe (Issue #0097),
 * werden alle Plattform-Admins per Mail benachrichtigt (Issue #0098).
 *
 * <p>Der Verbrauch läuft über {@link SingleUseTokenRepository#consume} und damit atomar in der
 * Datenbank. Verifikation <em>und</em> Admin-Benachrichtigung hängen daran: Bei gleichzeitigem
 * Einlösen desselben Tokens gewinnt genau ein Aufruf, und die Admins werden genau einmal
 * benachrichtigt (Issue #497).
 *
 * <p><strong>Fehlersemantik seit Issue #502:</strong> Die Admin-Benachrichtigung wird in derselben
 * Transaktion in der Outbox vorgemerkt und nach dem Commit mit Wiederholungen versandt — ein
 * HTTP-Erfolg bestätigt die Verifikation, nicht die Zustellung. Als zweite Verteidigungslinie neben
 * dem atomaren Tokenverbrauch dedupliziert der Idempotenzschlüssel der Outbox (Admin, neuer Nutzer)
 * eine doppelt eingeplante Benachrichtigung auf höchstens eine Zustellung.
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

    Long userId =
        tokens
            .consume(SecureTokens.sha256Hex(plaintextToken), now)
            .orElseThrow(InvalidVerificationTokenException::new);

    AppUser user = users.findById(userId).orElseThrow(InvalidVerificationTokenException::new);

    AppUser verified = users.save(user.withEmailVerified(true));

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
