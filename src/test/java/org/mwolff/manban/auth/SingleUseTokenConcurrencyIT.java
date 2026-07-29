package org.mwolff.manban.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TransactionRace;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.application.EmailVerificationTokenRepository;
import org.mwolff.manban.auth.application.InvalidResetTokenException;
import org.mwolff.manban.auth.application.InvalidVerificationTokenException;
import org.mwolff.manban.auth.application.PasswordResetTokenRepository;
import org.mwolff.manban.auth.application.ResetPasswordService;
import org.mwolff.manban.auth.application.VerifyEmailService;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.EmailVerificationToken;
import org.mwolff.manban.auth.domain.PasswordResetToken;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.SecureTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Weist nach, dass ein Einmal-Token auch bei echter Nebenläufigkeit genau einmal eingelöst wird
 * (Issue #497).
 *
 * <p><strong>Warum das deterministisch ist:</strong> Der Test verlässt sich nicht darauf, dass zwei
 * Threads „zufällig gleichzeitig" laufen — {@link TransactionRace} synchronisiert den Ablauf über
 * Zustände. Transaktion A löst das Token ein und hält offen (die Zeile ist gesperrt); B läuft in
 * genau diese Zeilensperre; erst wenn B nachweislich wartet, committet A. PostgreSQL wertet B's
 * {@code WHERE}-Bedingung dann auf der neuen Zeilenversion erneut aus — {@code used_at} ist nun
 * gesetzt, B trifft null Zeilen.
 *
 * <p>Mit der Vorgänger-Implementierung (lesen, prüfen, schreiben) hätte B noch {@code used_at =
 * NULL} gesehen (READ COMMITTED), die Prüfung bestanden und den Wert überschrieben — beide Aufrufe
 * wären erfolgreich gewesen.
 *
 * <p>Die Kontext-Konfiguration ist bewusst identisch mit den übrigen {@code
 * WebEnvironment.NONE}-ITs (kein {@code @TestConfiguration}, kein Mock-Bean): Ein eigener
 * Spring-Kontext brächte einen weiteren Verbindungspool mit und sprengte die {@code
 * max_connections} des geteilten Postgres-Containers. Dass die Admin-Benachrichtigung nur im
 * Gewinnerpfad läuft, prüft deshalb der Unit-Test {@code VerifyEmailServiceTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SingleUseTokenConcurrencyIT extends AbstractIntegrationTest {

  private static final String PASSWORD_A = "gewinner-passwort-1";
  private static final String PASSWORD_B = "verlierer-passwort-2";

  @Autowired private ResetPasswordService resetPasswordService;
  @Autowired private VerifyEmailService verifyEmailService;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordResetTokenRepository resetTokens;
  @Autowired private EmailVerificationTokenRepository verificationTokens;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  @Test
  void resetToken_isConsumedByExactlyOneOfTwoConcurrentTransactions() throws Exception {
    // Given: ein verifizierter Nutzer mit genau einem gültigen Reset-Token.
    Long userId = saveUser("race-reset@example.com", true);
    String plaintext = "reset-token-im-rennen";
    resetTokens.save(
        new PasswordResetToken(
            null,
            userId,
            SecureTokens.sha256Hex(plaintext),
            Instant.now().plusSeconds(3600),
            null));

    // When: zwei Transaktionen lösen dasselbe Token ein, B blockiert nachweislich an A's Sperre.
    TransactionRace.Result race =
        race(
            () -> resetPasswordService.reset(plaintext, PASSWORD_A),
            () -> resetPasswordService.reset(plaintext, PASSWORD_B));

    // Then: A gewinnt, B erhält die unspezifische Ablehnung.
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isInstanceOf(InvalidResetTokenException.class);

    // Und: nur das Passwort des Gewinners ist gesetzt.
    String storedHash = users.findById(userId).orElseThrow().passwordHash();
    assertThat(passwordEncoder.matches(PASSWORD_A, storedHash)).isTrue();
    assertThat(passwordEncoder.matches(PASSWORD_B, storedHash)).isFalse();
  }

  @Test
  void verificationToken_isConsumedByExactlyOneOfTwoConcurrentTransactions() throws Exception {
    // Given: ein freigabepflichtiger Nutzer mit einem gültigen Verifikations-Token.
    Long userId = saveUser("race-verify@example.com", false);
    String plaintext = "verify-token-im-rennen";
    verificationTokens.save(
        new EmailVerificationToken(
            null,
            userId,
            SecureTokens.sha256Hex(plaintext),
            Instant.now().plusSeconds(3600),
            null));

    // When
    TransactionRace.Result race =
        race(
            () -> verifyEmailService.verify(plaintext), () -> verifyEmailService.verify(plaintext));

    // Then: genau eine Einlösung — der Verlierer bricht vor jeder Wirkung ab.
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isInstanceOf(InvalidVerificationTokenException.class);
    assertThat(users.findById(userId).orElseThrow().emailVerified()).isTrue();
  }

  /** Kurzform für das gemeinsame Renn-Harness. */
  private TransactionRace.Result race(Runnable first, Runnable second) throws InterruptedException {
    return new TransactionRace(transactionManager, dataSource).run(first, second);
  }

  private Long saveUser(String email, boolean approved) {
    AppUser user =
        approved
            ? new AppUser(
                null,
                email,
                passwordEncoder.encode("alt-passwort-0"),
                "Person",
                false,
                PlatformRole.USER)
            : new AppUser(
                null,
                email,
                passwordEncoder.encode("alt-passwort-0"),
                "Person",
                false,
                PlatformRole.USER,
                null,
                null);
    return users.save(user).id();
  }
}
