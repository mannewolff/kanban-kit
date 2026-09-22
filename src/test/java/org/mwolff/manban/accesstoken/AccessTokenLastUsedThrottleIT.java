package org.mwolff.manban.accesstoken;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.StellbareUhrConfig;
import org.mwolff.manban.TransactionRace;
import org.mwolff.manban.accesstoken.application.AccessTokenService;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.ratelimit.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Weist gegen echtes PostgreSQL nach, dass der Nutzungsstempel gedrosselt geschrieben wird (Issue
 * #997, Plan #995 E6).
 *
 * <p>Zuvor schrieb jede Auflösung eines Tokens {@code last_used_at} — in der Auflösungstransaktion
 * und damit mit einer Zeilensperre, die bis zu deren Ende bestand. Genau die zehn gleichzeitigen
 * Befehle einer Person teilen sich <em>eine</em> Token-Zeile: Sie konnten strukturell nicht
 * parallel laufen. Seit #997 fällt die Entscheidung „schreiben oder nicht" aus einem prozesslokalen
 * Zwischenspeicher, und der Schreibvorgang läuft in einer eigenen, kurzen Transaktion.
 *
 * <p>Die Zeit steuert eine stellbare Uhr ({@link StellbareUhrConfig}): Ob die Minutengrenze
 * <em>wieder freigibt</em>, ist mit einer laufenden Uhr nur durch Warten prüfbar. Die Uhr wird vor
 * jeder Testmethode weitergestellt — der Zwischenspeicher liegt im Arbeitsspeicher des
 * Spring-Kontexts und überlebt das {@code TRUNCATE} der Basisklasse, während die Token-IDs durch
 * {@code RESTART IDENTITY} wieder bei 1 beginnen. Ohne das Weiterstellen träfe die zweite
 * Testmethode auf den Stempel der ersten.
 *
 * <p>Die Kontext-Konfiguration ist bewusst identisch mit {@link RevokeRaceIT} — beide teilen sich
 * damit einen Spring-Kontext und dessen Verbindungspool (Issue #900).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(StellbareUhrConfig.class)
class AccessTokenLastUsedThrottleIT extends AbstractIntegrationTest {

  /** Zehn gleichzeitige Befehle einer Person — die zugesagte Gleichzeitigkeit aus #970. */
  private static final int PARALLEL_COMMANDS = 10;

  /**
   * Kurzer Verbindungs-Timeout, damit ein Engpass als Fehler statt als 30-Sekunden-Warten endet.
   */
  private static final Duration SHORT_POOL_TIMEOUT = Duration.ofSeconds(2);

  @Autowired private AccessTokenService accessTokens;
  @Autowired private AppUserRepository users;
  @Autowired private MutableClock clock;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  @BeforeEach
  void letTheThrottleForgetPreviousMethods() {
    clock.advance(Duration.ofMinutes(10));
  }

  @Test
  void resolveBinding_stampsOncePerMinute_andAgainAfterIt() {
    // Given: ein Nutzer mit einem aktiven Token.
    long userId = user("throttle@example.com");
    AccessTokenService.CreatedAccessToken token = accessTokens.create(userId, "CI", null, null);
    Instant firstUse = clock.instant();

    // When: drei Auflösungen innerhalb einer Minute.
    accessTokens.resolveBinding(token.plaintext());
    clock.advance(Duration.ofSeconds(30));
    accessTokens.resolveBinding(token.plaintext());
    clock.advance(Duration.ofSeconds(29));
    accessTokens.resolveBinding(token.plaintext());

    // Then: geschrieben wurde genau einmal — es steht noch der Stempel der ersten Auflösung.
    assertThat(lastUsedAtInDatabase(token.id())).isEqualTo(firstUse);

    // When: die Minute ist um.
    clock.advance(Duration.ofSeconds(1));
    Instant afterTheMinute = clock.instant();
    accessTokens.resolveBinding(token.plaintext());

    // Then: „zuletzt benutzt" ist wieder aktuell — minutengenau, nicht aufrufgenau.
    assertThat(lastUsedAtInDatabase(token.id())).isEqualTo(afterTheMinute);
  }

  @Test
  void resolveBinding_stampsTokensSeparately() {
    // Given: zwei Token desselben Nutzers.
    long userId = user("throttle-two@example.com");
    AccessTokenService.CreatedAccessToken first = accessTokens.create(userId, "A", null, null);
    AccessTokenService.CreatedAccessToken second = accessTokens.create(userId, "B", null, null);
    Instant now = clock.instant();

    // When: beide werden in derselben Minute benutzt.
    accessTokens.resolveBinding(first.plaintext());
    accessTokens.resolveBinding(second.plaintext());

    // Then: die Drosselung zählt je Token, nicht je Person.
    assertThat(lastUsedAtInDatabase(first.id())).isEqualTo(now);
    assertThat(lastUsedAtInDatabase(second.id())).isEqualTo(now);
  }

  @Test
  void revokedAndUnknownTokens_areStillRejected_andLeaveNoStamp() {
    // Given: ein widerrufenes Token. Ein „abgelaufenes" gibt es in diesem Modell nicht — die
    // Tabelle kennt kein Ablaufdatum (V1, `kanban_access_token`); der Widerruf ist der einzige
    // Endzustand. Der dritte Fall ist das unbekannte Token.
    long userId = user("throttle-revoked@example.com");
    AccessTokenService.CreatedAccessToken token = accessTokens.create(userId, "CI", null, null);
    accessTokens.revoke(userId, token.id());

    // When / Then: die Prüfung bleibt unverändert — die Drosselung betrifft nur den Stempel.
    assertThat(accessTokens.resolveBinding(token.plaintext())).isEmpty();
    assertThat(accessTokens.resolveBinding("tk_gibt-es-nicht")).isEmpty();
    assertThat(lastUsedAtInDatabase(token.id())).isNull();
  }

  @Test
  void concurrentResolutions_ofTheSameToken_doNotBlockEachOther() throws Exception {
    // Given: ein Token, zwei Auflösungen in echten, gleichzeitig offenen Transaktionen.
    long userId = user("throttle-race@example.com");
    AccessTokenService.CreatedAccessToken token = accessTokens.create(userId, "CI", null, null);

    // When: die zweite Auflösung läuft, während die erste ihre Transaktion offen hält.
    // Vor #997 stempelte die erste Auflösung in ihrer eigenen Transaktion und hielt damit die
    // Zeilensperre — die zweite wartete. runUnblocked schlägt genau dann fehl.
    TransactionRace.Result race =
        new TransactionRace(transactionManager, dataSource)
            .runUnblocked(
                () -> accessTokens.resolveBinding(token.plaintext()),
                () -> accessTokens.resolveBinding(token.plaintext()));

    // Then
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
  }

  @Test
  void tenConcurrentResolutions_allSucceed() throws Exception {
    // Given: zehn gleichzeitige Befehle einer Person auf demselben Token (Lastprofil aus #970).
    long userId = user("throttle-ten@example.com");
    AccessTokenService.CreatedAccessToken token = accessTokens.create(userId, "CI", null, null);
    Instant now = clock.instant();
    List<Optional<KanbanPrincipal>> resolved = Collections.synchronizedList(new ArrayList<>());
    List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(PARALLEL_COMMANDS);

    // When
    IntStream.range(0, PARALLEL_COMMANDS)
        .forEach(
            i -> {
              Thread thread =
                  new Thread(
                      () -> {
                        try {
                          gate.await();
                          resolved.add(accessTokens.resolveBinding(token.plaintext()));
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                          failures.add(e);
                        } finally {
                          done.countDown();
                        }
                      },
                      "resolve-" + i);
              thread.setUncaughtExceptionHandler((t, failure) -> failures.add(failure));
              thread.start();
            });
    gate.countDown();

    // Then: alle laufen durch — keiner wartet auf einen anderen.
    assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
    assertThat(failures).isEmpty();
    assertThat(resolved).hasSize(PARALLEL_COMMANDS).allSatisfy(r -> assertThat(r).isPresent());
    assertThat(lastUsedAtInDatabase(token.id())).isEqualTo(now);
  }

  @Test
  void resolveBinding_holdsOneConnectionOnly_evenWhenItStamps() throws Exception {
    // Given: ein frisches Token, dessen Stempel fällig ist, und ein Pool mit genau einer freien
    // Verbindung. Das ist die Lage unter Last: Treffen so viele Auflösungen fälliger Token
    // gleichzeitig ein, wie der Pool Verbindungen hat, hat jede genau eine. Hielte die Auflösung
    // ihre Leseverbindung, während der Stempel eine zweite holt, warteten alle aufeinander, bis
    // der Verbindungs-Timeout den Filter mit einem Fehler abbricht.
    long userId = user("throttle-pool@example.com");
    AccessTokenService.CreatedAccessToken token = accessTokens.create(userId, "CI", null, null);
    HikariConfigMXBean pool = dataSource.unwrap(HikariDataSource.class).getHikariConfigMXBean();
    long originalTimeout = pool.getConnectionTimeout();
    pool.setConnectionTimeout(SHORT_POOL_TIMEOUT.toMillis());
    try (HeldConnections held = holdAllButOne(pool.getMaximumPoolSize())) {
      assertThat(held.count()).isEqualTo(pool.getMaximumPoolSize() - 1);

      // When
      Optional<KanbanPrincipal> resolved = accessTokens.resolveBinding(token.plaintext());

      // Then: aufgelöst und gestempelt — mit der einen freien Verbindung.
      assertThat(resolved).map(KanbanPrincipal::userId).contains(userId);
    } finally {
      pool.setConnectionTimeout(originalTimeout);
    }
    assertThat(lastUsedAtInDatabase(token.id())).isEqualTo(clock.instant());
  }

  // --- Fixtures ------------------------------------------------------------

  private long user(String email) {
    return users.save(new AppUser(null, email, "hash", "A", true, PlatformRole.USER)).requireId();
  }

  /**
   * Der Stempel direkt aus der Tabelle — bewusst per SQL statt über den Port: Die Erwartung soll
   * den Datenbankstand prüfen, nicht die Sicht der Anwendung.
   */
  private @Nullable Instant lastUsedAtInDatabase(long tokenId) {
    OffsetDateTime stamp =
        new JdbcTemplate(dataSource)
            .queryForObject(
                "SELECT last_used_at FROM kanban_access_token WHERE id = ?",
                OffsetDateTime.class,
                tokenId);
    return stamp == null ? null : stamp.toInstant();
  }

  /** Belegt alle Verbindungen des Pools bis auf eine — und gibt sie beim Schließen zurück. */
  private HeldConnections holdAllButOne(int poolSize) throws SQLException {
    HeldConnections held = new HeldConnections();
    try {
      for (int i = 0; i < poolSize - 1; i++) {
        held.connections.add(dataSource.getConnection());
      }
    } catch (SQLException e) {
      held.close();
      throw e;
    }
    return held;
  }

  /** Die belegten Verbindungen als eine Ressource, damit try-with-resources sie freigibt. */
  private static final class HeldConnections implements AutoCloseable {

    private final List<Connection> connections = new ArrayList<>();

    int count() {
      return connections.size();
    }

    @Override
    // PMD.CloseResource: Fehlalarm — die Schleifenvariable ist die Verbindung, die hier schließt.
    @SuppressWarnings("PMD.CloseResource")
    public void close() throws SQLException {
      for (Connection connection : connections) {
        connection.close();
      }
    }
  }
}
