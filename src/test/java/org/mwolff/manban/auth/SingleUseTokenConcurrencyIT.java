package org.mwolff.manban.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Weist nach, dass ein Einmal-Token auch bei echter Nebenläufigkeit genau einmal eingelöst wird
 * (Issue #497).
 *
 * <p><strong>Warum das deterministisch ist:</strong> Der Test verlässt sich nicht darauf, dass zwei
 * Threads „zufällig gleichzeitig" laufen. Der Ablauf ist über Zustände synchronisiert:
 *
 * <ol>
 *   <li>Transaktion A löst das Token ein und hält offen — die Zeile ist damit gesperrt.
 *   <li>Transaktion B startet erst danach und läuft in genau diese Zeilensperre.
 *   <li>Der Test wartet über {@code pg_stat_activity}, bis B nachweislich auf der Sperre wartet
 *       (Zustandsabfrage, keine Wartezeit-Annahme).
 *   <li>Erst dann committet A. PostgreSQL wertet B's {@code WHERE}-Bedingung auf der neuen
 *       Zeilenversion erneut aus — {@code used_at} ist nun gesetzt, B trifft null Zeilen.
 * </ol>
 *
 * <p>Mit der Vorgänger-Implementierung (lesen, prüfen, schreiben) hätte B in Schritt 2 noch {@code
 * used_at = NULL} gesehen (READ COMMITTED), die Prüfung bestanden und den Wert überschrieben —
 * beide Aufrufe wären erfolgreich gewesen.
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

  /** Obergrenze für das Warten auf einen Zustand; nur Notausgang, nicht Teil der Logik. */
  private static final long DEADLINE_SECONDS = 30;

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
    RaceResult race =
        race(
            () -> resetPasswordService.reset(plaintext, PASSWORD_A),
            () -> resetPasswordService.reset(plaintext, PASSWORD_B));

    // Then: A gewinnt, B erhält die unspezifische Ablehnung.
    assertThat(race.winnerFailure()).isNull();
    assertThat(race.loserFailure()).isInstanceOf(InvalidResetTokenException.class);

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
    RaceResult race =
        race(
            () -> verifyEmailService.verify(plaintext), () -> verifyEmailService.verify(plaintext));

    // Then: genau eine Einlösung — der Verlierer bricht vor jeder Wirkung ab.
    assertThat(race.winnerFailure()).isNull();
    assertThat(race.loserFailure()).isInstanceOf(InvalidVerificationTokenException.class);
    assertThat(users.findById(userId).orElseThrow().emailVerified()).isTrue();
  }

  /** Ausgang eines Rennens: was der zuerst startende und was der zweite Aufruf geworfen hat. */
  private record RaceResult(@Nullable Throwable winnerFailure, @Nullable Throwable loserFailure) {}

  /**
   * Führt {@code first} und {@code second} in zwei echten Transaktionen so aus, dass {@code second}
   * garantiert auf der von {@code first} gehaltenen Zeilensperre wartet, bevor {@code first}
   * committet.
   */
  private RaceResult race(Runnable first, Runnable second) throws InterruptedException {
    TransactionTemplate transactions = new TransactionTemplate(transactionManager);
    CountDownLatch firstConsumed = new CountDownLatch(1);
    CountDownLatch firstMayCommit = new CountDownLatch(1);
    AtomicReference<Throwable> firstFailure = new AtomicReference<>();
    AtomicReference<Throwable> secondFailure = new AtomicReference<>();

    // Fehlschläge werden über den UncaughtExceptionHandler eingesammelt statt über einen
    // catch-Block: So landet jeder Throwable im Ergebnis, ohne pauschal Exceptions zu fangen.
    Thread firstThread =
        new Thread(
            () -> {
              try {
                transactions.executeWithoutResult(
                    status -> {
                      first.run();
                      firstConsumed.countDown();
                      awaitLatch(firstMayCommit);
                    });
              } finally {
                firstConsumed.countDown(); // auch bei Fehlschlag weiterlaufen
              }
            },
            "token-race-first");
    firstThread.setUncaughtExceptionHandler((thread, failure) -> firstFailure.set(failure));

    Thread secondThread =
        new Thread(
            () -> transactions.executeWithoutResult(status -> second.run()), "token-race-second");
    secondThread.setUncaughtExceptionHandler((thread, failure) -> secondFailure.set(failure));

    try {
      firstThread.start();
      awaitLatch(firstConsumed);
      secondThread.start();
      awaitBlockedOnRowLock();
    } finally {
      firstMayCommit.countDown();
    }
    firstThread.join(TimeUnit.SECONDS.toMillis(DEADLINE_SECONDS));
    secondThread.join(TimeUnit.SECONDS.toMillis(DEADLINE_SECONDS));

    return new RaceResult(firstFailure.get(), secondFailure.get());
  }

  /**
   * Wartet, bis eine Datenbanksitzung auf eine Sperre wartet. Das ist eine Zustandsabfrage: Der
   * Test schreitet erst fort, wenn die Blockade tatsächlich eingetreten ist — nicht nach Ablauf
   * einer geschätzten Wartezeit.
   */
  private void awaitBlockedOnRowLock() throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DEADLINE_SECONDS);
    while (System.nanoTime() < deadline) {
      if (sessionsWaitingOnLock() > 0) {
        return;
      }
      TimeUnit.MILLISECONDS.sleep(20);
    }
    throw new IllegalStateException(
        "Die zweite Transaktion wartet nicht auf der Zeilensperre —"
            + " der Verbrauch ist nicht atomar.");
  }

  private int sessionsWaitingOnLock() {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT count(*) FROM pg_stat_activity"
                    + " WHERE wait_event_type = 'Lock' AND datname = current_database()")) {
      return result.next() ? result.getInt(1) : 0;
    } catch (SQLException e) {
      throw new IllegalStateException("pg_stat_activity nicht abfragbar", e);
    }
  }

  private static void awaitLatch(CountDownLatch latch) {
    try {
      if (!latch.await(DEADLINE_SECONDS, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Erwarteter Zustand trat nicht ein");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
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
