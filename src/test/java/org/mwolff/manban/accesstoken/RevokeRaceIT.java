package org.mwolff.manban.accesstoken;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
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
 * Weist gegen echtes PostgreSQL nach, dass ein Widerruf ein <strong>Endzustand</strong> ist (Issue
 * #878, fachlich #836, Kriterium 4).
 *
 * <p>Zuvor schrieb jede Token-Nutzung den vollständigen Datensatz aus einem zuvor gelesenen Zustand
 * zurück. Fiel ein Widerruf zwischen Lesen und Zurückschreiben, überschrieb der alte Snapshot
 * {@code revoked = true} — das Token lebte weiter, obwohl die Liste es als widerrufen zeigte. Seit
 * #878 schreiben Nutzung und Widerruf disjunkte Spalten: {@code touchLastUsedAtInOwnTransaction}
 * setzt allein {@code last_used_at} und nur unter der Bedingung {@code revoked = false}, {@code
 * markRevoked} allein {@code revoked}.
 *
 * <p>{@link TransactionRace#run} erzwingt die Lage deterministisch statt zeitabhängig: Der zweite
 * Aufruf muss nachweislich auf einer Sperre des ersten warten, sonst schlägt der Lauf fehl. Jeder
 * Test ist damit seine eigene Gegenprobe.
 *
 * <p><strong>Seit Issue #997</strong> stempelt die Nutzung gedrosselt und in einer <em>eigenen,
 * kurzen</em> Transaktion. Die Richtung „Widerruf zuerst" bleibt damit ein Warten auf einer Sperre
 * — der Stempel-Schreibvorgang läuft in die Zeilensperre des Widerrufs. Die Gegenrichtung ist keine
 * mehr: Eine laufende Nutzung hält über ihre Auflösung hinweg <em>keine</em> Sperre, und genau das
 * ist der Zweck von #997. Sie wird deshalb mit {@link TransactionRace#runUnblocked} geprüft, das
 * denselben Sachverhalt andersherum festhält.
 *
 * <p>Die stellbare Uhr ({@link StellbareUhrConfig}) ist Bedingung der Determiniertheit: Der
 * Drosselungsspeicher liegt im Arbeitsspeicher des Spring-Kontexts und überlebt das {@code
 * TRUNCATE} der Basisklasse, während die Token-IDs durch {@code RESTART IDENTITY} wieder bei 1
 * beginnen. Ohne das Weiterstellen der Uhr hielte die zweite Testmethode den Stempel der ersten für
 * frisch, unterließe den Schreibvorgang — und das erwartete Warten auf der Sperre träte nie ein.
 * Die Konfiguration ist bewusst identisch mit {@link AccessTokenLastUsedThrottleIT}, damit beide
 * sich einen Kontext und dessen Verbindungspool teilen (Issue #900).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(StellbareUhrConfig.class)
class RevokeRaceIT extends AbstractIntegrationTest {

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
  void concurrentUse_doesNotRevive_tokenRevokedInParallel() throws Exception {
    // Given: ein Nutzer mit einem aktiven Token.
    long userId = user("revoke-race@example.com");
    AccessTokenService.CreatedAccessToken token = accessTokens.create(userId, "CI", null, null);
    long tokenId = token.id();

    // When: der Widerruf hält seine Zeilensperre, während die Nutzung hineinläuft — genauer:
    // deren Stempel-Schreibvorgang, der seit #997 in einer eigenen Transaktion läuft.
    TransactionRace.Result race =
        race(
            () -> accessTokens.revoke(userId, tokenId),
            () -> accessTokens.resolveBinding(token.plaintext()));

    // Then: kein Aufruf scheitert — und der Widerruf bleibt stehen.
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    assertThat(revokedInDatabase(tokenId)).isTrue();
    assertThat(accessTokens.list(userId))
        .singleElement()
        .extracting(AccessTokenService.AccessTokenView::revoked)
        .isEqualTo(true);
    // Die nächste Anfrage mit dem Token wird abgewiesen: kein Principal, also keine
    // Authentifizierung.
    assertThat(accessTokens.resolveBinding(token.plaintext())).isEmpty();
  }

  @Test
  void revocation_completes_whileTheUseIsStillInFlight() throws Exception {
    // Given: dieselbe Lage, nur andersherum — die Nutzung war zuerst da.
    long userId = user("revoke-race-inflight@example.com");
    AccessTokenService.CreatedAccessToken token = accessTokens.create(userId, "CI", null, null);
    long tokenId = token.id();
    AtomicReference<Optional<KanbanPrincipal>> resolved = new AtomicReference<>(Optional.empty());

    // When: der Widerruf läuft, während die Transaktion der Nutzung noch offen ist. Seit #997
    // hält die Auflösung dabei keine Sperre mehr — ihr Stempel ist längst in einer eigenen,
    // kurzen Transaktion committet. runUnblocked schlägt fehl, wenn der Widerruf doch wartet.
    TransactionRace.Result race =
        raceUnblocked(
            () -> resolved.set(accessTokens.resolveBinding(token.plaintext())),
            () -> accessTokens.revoke(userId, tokenId));

    // Then: die bereits eingegangene Anfrage läuft zu Ende …
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    assertThat(resolved.get()).map(KanbanPrincipal::userId).contains(userId);
    // … und der Widerruf bleibt stehen.
    assertThat(revokedInDatabase(tokenId)).isTrue();
    assertThat(accessTokens.resolveBinding(token.plaintext())).isEmpty();
  }

  // --- Fixtures ------------------------------------------------------------

  private TransactionRace.Result race(Runnable first, Runnable second) throws InterruptedException {
    return new TransactionRace(transactionManager, dataSource).run(first, second);
  }

  private TransactionRace.Result raceUnblocked(Runnable first, Runnable second)
      throws InterruptedException {
    return new TransactionRace(transactionManager, dataSource).runUnblocked(first, second);
  }

  private long user(String email) {
    return users.save(new AppUser(null, email, "hash", "A", true, PlatformRole.USER)).requireId();
  }

  /**
   * Der Widerrufsstatus direkt aus der Tabelle — bewusst per SQL statt über den Port: Die Erwartung
   * soll den Datenbankstand prüfen, nicht die Sicht der Anwendung.
   */
  private boolean revokedInDatabase(long tokenId) {
    return Boolean.TRUE.equals(
        new JdbcTemplate(dataSource)
            .queryForObject(
                "SELECT revoked FROM kanban_access_token WHERE id = ?", Boolean.class, tokenId));
  }
}
