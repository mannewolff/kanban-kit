package org.mwolff.manban.accesstoken;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TransactionRace;
import org.mwolff.manban.accesstoken.application.AccessTokenService;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Weist gegen echtes PostgreSQL nach, dass ein Widerruf ein <strong>Endzustand</strong> ist (Issue
 * #878, fachlich #836, Kriterium 4).
 *
 * <p>Zuvor schrieb jede Token-Nutzung den vollständigen Datensatz aus einem zuvor gelesenen Zustand
 * zurück. Fiel ein Widerruf zwischen Lesen und Zurückschreiben, überschrieb der alte Snapshot
 * {@code revoked = true} — das Token lebte weiter, obwohl die Liste es als widerrufen zeigte. Seit
 * #878 schreiben Nutzung und Widerruf disjunkte Spalten: {@code touchLastUsedAt} setzt allein
 * {@code last_used_at} und nur unter der Bedingung {@code revoked = false}, {@code markRevoked}
 * allein {@code revoked}.
 *
 * <p>{@link TransactionRace#run} erzwingt die Lage deterministisch statt zeitabhängig: Der zweite
 * Aufruf muss nachweislich auf einer Sperre des ersten warten, sonst schlägt der Lauf fehl. Jeder
 * Test ist damit seine eigene Gegenprobe.
 *
 * <p>Die Kontext-Konfiguration ist bewusst identisch mit den übrigen {@code
 * WebEnvironment.NONE}-ITs: Ein eigener Spring-Kontext brächte einen weiteren Verbindungspool mit
 * und sprengte die {@code max_connections} des geteilten Postgres-Containers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RevokeRaceIT extends AbstractIntegrationTest {

  @Autowired private AccessTokenService accessTokens;
  @Autowired private AppUserRepository users;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  @Test
  void concurrentUse_doesNotRevive_tokenRevokedInParallel() throws Exception {
    // Given: ein Nutzer mit einem aktiven Token.
    long userId = user("revoke-race@example.com");
    AccessTokenService.CreatedAccessToken token = accessTokens.create(userId, "CI", null, null);
    long tokenId = token.id();

    // When: der Widerruf hält seine Zeilensperre, während die Nutzung hineinläuft.
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
  void useAlreadyInFlight_completes_whileRevocationWaits() throws Exception {
    // Given: dieselbe Lage, nur andersherum — die Nutzung war zuerst da.
    long userId = user("revoke-race-inflight@example.com");
    AccessTokenService.CreatedAccessToken token = accessTokens.create(userId, "CI", null, null);
    long tokenId = token.id();
    AtomicReference<Optional<KanbanPrincipal>> resolved = new AtomicReference<>(Optional.empty());

    // When: der Widerruf läuft in die Sperre der laufenden Nutzung.
    TransactionRace.Result race =
        race(
            () -> resolved.set(accessTokens.resolveBinding(token.plaintext())),
            () -> accessTokens.revoke(userId, tokenId));

    // Then: die bereits eingegangene Anfrage läuft zu Ende …
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    assertThat(resolved.get()).map(KanbanPrincipal::userId).contains(userId);
    // … und der danach committete Widerruf bleibt trotzdem stehen.
    assertThat(revokedInDatabase(tokenId)).isTrue();
    assertThat(accessTokens.resolveBinding(token.plaintext())).isEmpty();
  }

  // --- Fixtures ------------------------------------------------------------

  private TransactionRace.Result race(Runnable first, Runnable second) throws InterruptedException {
    return new TransactionRace(transactionManager, dataSource).run(first, second);
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
