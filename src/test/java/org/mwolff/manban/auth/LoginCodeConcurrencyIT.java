package org.mwolff.manban.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TransactionRace;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.application.LoginCodeRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.LoginCode;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Weist nach, dass eine Code-Anforderung auch bei echter Nebenläufigkeit genau einmal eingelöst
 * wird und Fehlversuche das Maximum nicht überschreiten (Plan #1801, A2).
 *
 * <p>Wie {@link SingleUseTokenConcurrencyIT} synchronisiert {@link TransactionRace} über Zustände:
 * A hält die Zeilensperre offen, B wartet nachweislich darauf, erst dann committet A. PostgreSQL
 * wertet B's {@code WHERE} auf der neuen Zeilenversion aus — B trifft keine Zeile mehr. Ein Lesen,
 * Prüfen und Schreiben nacheinander ließe unter READ COMMITTED beide gewinnen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LoginCodeConcurrencyIT extends AbstractIntegrationTest {

  private static final int MAX = 5;
  private static final String EMAIL = "email-hash-rennen";
  private static final String CODE = "code-hmac-rennen";
  private static final String LINK = "link-hash-rennen";

  @Autowired private LoginCodeRepository codes;
  @Autowired private AppUserRepository users;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  private final Instant now = Instant.now();

  @Test
  void vonZweiGleichzeitigenCodeEinloesungenGewinntGenauEine() throws Exception {
    Long userId = anforderung(CODE, LINK);
    AtomicReference<Optional<Long>> erste = new AtomicReference<>();
    AtomicReference<Optional<Long>> zweite = new AtomicReference<>();

    TransactionRace.Result race =
        race(
            () -> erste.set(codes.consumeByCode(EMAIL, CODE, now, MAX)),
            () -> zweite.set(codes.consumeByCode(EMAIL, CODE, now, MAX)));

    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    assertThat(erste.get()).hasValue(userId);
    assertThat(zweite.get()).isEmpty();
  }

  @Test
  void vonZweiGleichzeitigenLinkEinloesungenGewinntGenauEine() throws Exception {
    Long userId = anforderung(CODE, LINK);
    AtomicReference<Optional<Long>> erste = new AtomicReference<>();
    AtomicReference<Optional<Long>> zweite = new AtomicReference<>();

    TransactionRace.Result race =
        race(
            () -> erste.set(codes.consumeByLink(LINK, now, MAX)),
            () -> zweite.set(codes.consumeByLink(LINK, now, MAX)));

    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    assertThat(erste.get()).hasValue(userId);
    assertThat(zweite.get()).isEmpty();
  }

  @Test
  void vonCodeUndLinkDerselbenAnforderungGewinntGenauEiner() throws Exception {
    Long userId = anforderung(CODE, LINK);
    AtomicReference<Optional<Long>> link = new AtomicReference<>();
    AtomicReference<Optional<Long>> code = new AtomicReference<>();

    race(
        () -> link.set(codes.consumeByLink(LINK, now, MAX)),
        () -> code.set(codes.consumeByCode(EMAIL, CODE, now, MAX)));

    assertThat(link.get()).hasValue(userId);
    assertThat(code.get()).isEmpty();
  }

  @Test
  void gleichzeitigeFehlversucheUeberschreitenDasMaximumNicht() throws Exception {
    Long id = anforderungMitId(CODE, LINK);
    jdbc.update("UPDATE login_code SET failed_attempts = ? WHERE id = ?", MAX - 1, id);
    AtomicInteger erste = new AtomicInteger(-1);
    AtomicInteger zweite = new AtomicInteger(-1);

    race(
        () -> erste.set(codes.recordFailure(EMAIL, now, MAX)),
        () -> zweite.set(codes.recordFailure(EMAIL, now, MAX)));

    assertThat(erste.get()).isEqualTo(1);
    assertThat(zweite.get()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT failed_attempts FROM login_code WHERE id = ?", Integer.class, id))
        .isEqualTo(MAX);
  }

  @Test
  void einFehlversuchUndEineEinloesungAmLetztenVersuch() throws Exception {
    Long id = anforderungMitId(CODE, LINK);
    jdbc.update("UPDATE login_code SET failed_attempts = ? WHERE id = ?", MAX - 1, id);
    AtomicReference<Optional<Long>> code = new AtomicReference<>();

    race(
        () -> codes.recordFailure(EMAIL, now, MAX),
        () -> code.set(codes.consumeByCode(EMAIL, CODE, now, MAX)));

    assertThat(code.get()).isEmpty();
  }

  @Test
  void eineNeueAnforderungEntwertetAltenCodeUndAltenLink() throws Exception {
    anforderung(CODE, LINK);
    AtomicReference<Optional<Long>> alterCode = new AtomicReference<>();

    race(
        () -> codes.supersedeOpen(EMAIL, now),
        () -> alterCode.set(codes.consumeByCode(EMAIL, CODE, now, MAX)));
    Long userId = anforderung("code-hmac-neu", "link-hash-neu");

    assertThat(alterCode.get()).isEmpty();
    assertThat(codes.consumeByLink(LINK, now, MAX)).isEmpty();
    assertThat(codes.consumeByCode(EMAIL, "code-hmac-neu", now, MAX)).hasValue(userId);
  }

  private Long anforderung(String codeHash, String linkHash) {
    Long id = anforderungMitId(codeHash, linkHash);
    return jdbc.queryForObject("SELECT user_id FROM login_code WHERE id = ?", Long.class, id);
  }

  private Long anforderungMitId(String codeHash, String linkHash) {
    Long userId =
        users
            .findByEmail("rennen@example.com")
            .map(AppUser::id)
            .orElseGet(
                () ->
                    users
                        .save(
                            new AppUser(
                                null,
                                "rennen@example.com",
                                "hash",
                                "Person",
                                true,
                                PlatformRole.USER))
                        .id());
    return codes
        .save(
            new LoginCode(
                null, EMAIL, userId, codeHash, linkHash, now, now.plusSeconds(600), 0, null, null))
        .id();
  }

  /** Kurzform für das gemeinsame Renn-Harness. */
  private TransactionRace.Result race(Runnable first, Runnable second) throws InterruptedException {
    return new TransactionRace(transactionManager, dataSource).run(first, second);
  }
}
