package org.mwolff.manban.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.LoginCode;
import org.mwolff.manban.common.SecureTokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fordert einen Anmeldecode an (Plan #1801, A5, A6, A12).
 *
 * <p><strong>Nichts verrät das Konto:</strong> Für jede Adresse — unbekannt, gesperrt, wartend oder
 * freigegeben — zählt dieselbe Grenze, werden offene Anforderungen ersetzt und entsteht eine Zeile.
 * Für eine unbekannte Adresse trägt sie weder Konto noch Code; eine Mail geht nur an ein
 * bestehendes Konto. Gesperrte und wartende Konten bekommen ihren Code ebenfalls, abgewiesen werden
 * sie erst beim Einlösen. Die Antwort ist darum immer dieselbe.
 *
 * <p><strong>Fehlersemantik wie seit Issue #502:</strong> Die Mail wird in derselben Transaktion in
 * der Outbox vorgemerkt; ein Ausfall des Mailservers rollt die Anforderung nicht zurück.
 */
@Service
public class RequestLoginCodeService {

  /** Fenster der Grenze je Adresse. */
  private static final Duration WINDOW = Duration.ofHours(1);

  /** Ältere Zeilen löscht jede Anforderung mit; die Grenze braucht nur die letzte Stunde. */
  private static final Duration RETENTION = Duration.ofHours(24);

  private final AppUserRepository users;
  private final LoginCodeRepository codes;
  private final LoginCodeCrypto crypto;
  private final LoginCodeMailer mailer;
  private final AuthProperties properties;
  private final Clock clock;

  public RequestLoginCodeService(
      AppUserRepository users,
      LoginCodeRepository codes,
      LoginCodeCrypto crypto,
      LoginCodeMailer mailer,
      AuthProperties properties,
      Clock clock) {
    this.users = users;
    this.codes = codes;
    this.crypto = crypto;
    this.mailer = mailer;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Nimmt eine Anforderung für die Adresse an.
   *
   * @throws LoginCodeLimitExceededException ab der sechsten Anforderung je gleitender Stunde
   */
  @Transactional
  public void requestCode(String email) {
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    String emailHash = SecureTokens.sha256Hex(normalizedEmail);
    Instant now = clock.instant();
    codes.deleteOlderThan(now.minus(RETENTION));
    if (codes.countSince(emailHash, now.minus(WINDOW))
        >= properties.loginCode().maxRequestsPerHour()) {
      throw new LoginCodeLimitExceededException();
    }
    codes.supersedeOpen(emailHash, now);
    Instant expiresAt = now.plus(properties.loginCode().ttl());
    users
        .findByEmail(normalizedEmail)
        .ifPresentOrElse(
            user -> issue(user, emailHash, now, expiresAt),
            () ->
                codes.save(
                    new LoginCode(
                        null, emailHash, null, null, null, now, expiresAt, 0, null, null)));
  }

  private void issue(AppUser user, String emailHash, Instant now, Instant expiresAt) {
    String code = crypto.newCode();
    String linkToken = crypto.newLinkToken();
    codes.save(
        new LoginCode(
            null,
            emailHash,
            user.requireId(),
            crypto.codeHmac(code),
            crypto.linkHash(linkToken),
            now,
            expiresAt,
            0,
            null,
            null));
    mailer.sendLoginCode(
        user.email(), code, properties.baseUrl() + "/login/link?token=" + linkToken);
  }
}
