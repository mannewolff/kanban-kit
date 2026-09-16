package org.mwolff.manban.auth.application;

import java.time.Clock;
import java.time.Instant;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.common.SecureTokens;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Setzt das Passwort anhand eines gültigen Reset-Tokens neu und verbraucht das Token (einmalig).
 * Ungültige, abgelaufene oder bereits genutzte Tokens werden abgelehnt.
 *
 * <p>Der Verbrauch läuft über {@link SingleUseTokenRepository#consume} und damit atomar in der
 * Datenbank. Das neue Passwort wird bewusst erst danach geschrieben: Nur wer das Token gewonnen
 * hat, darf das Passwort ändern (Issue #497).
 *
 * <p>Der Reset beendet alle Sitzungen des Kontos (Issue #886): Wer sein Passwort neu setzt, wirft
 * damit jede bestehende Anmeldung hinaus — auch die eines Angreifers, der sich zuvor mit dem alten
 * Passwort angemeldet hatte. Das Hochzählen der Generation läuft in derselben Transaktion wie das
 * Schreiben des Passworts: Beides gilt gemeinsam oder gar nicht.
 */
@Service
public class ResetPasswordService {

  private final AppUserRepository users;
  private final PasswordResetTokenRepository tokens;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;
  private final SessionGenerations generations;

  public ResetPasswordService(
      AppUserRepository users,
      PasswordResetTokenRepository tokens,
      PasswordEncoder passwordEncoder,
      Clock clock,
      SessionGenerations generations) {
    this.users = users;
    this.tokens = tokens;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
    this.generations = generations;
  }

  @Transactional
  public void reset(String plaintextToken, String newRawPassword) {
    Instant now = clock.instant();

    Long userId =
        tokens
            .consume(SecureTokens.sha256Hex(plaintextToken), now)
            .orElseThrow(InvalidResetTokenException::new);

    AppUser user = users.findById(userId).orElseThrow(InvalidResetTokenException::new);

    users.save(user.withPasswordHash(passwordEncoder.encode(newRawPassword)));
    generations.invalidateSessions(userId);
  }
}
