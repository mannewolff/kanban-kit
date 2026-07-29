package org.mwolff.manban.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TransactionRace;
import org.mwolff.manban.outbox.application.OutboxRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Weist nach, dass ein zweiter Outbox-Worker einen bereits in Arbeit befindlichen Eintrag
 * überspringt, statt an ihm hängen zu bleiben oder ihn ein zweites Mal auszuführen (Issue #501).
 *
 * <p><strong>Warum das deterministisch ist:</strong> {@link TransactionRace} synchronisiert über
 * Zustände statt über Wartezeiten. Transaktion A greift den Eintrag und hält offen — die Zeile ist
 * gesperrt; B läuft in genau diese Sperre. Weil der Zugriff {@code SKIP LOCKED} nutzt, muss B
 * <em>durchlaufen</em> statt zu warten: dafür steht {@link TransactionRace#runUnblocked}, das genau
 * dann fehlschlägt, wenn B doch blockiert. Ein gewöhnliches {@code FOR UPDATE} ließe B warten und
 * würde hier auffallen.
 *
 * <p>Der Eintrag ist bewusst weit in der Zukunft fällig und wird mit einem entsprechend
 * vorgestellten Zeitpunkt gegriffen. So bleibt der Test im Standard-Kontext (kein eigener
 * Verbindungspool, siehe {@code OutboxIT}), ohne dass der nebenher laufende Worker ihn wegschnappt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OutboxConcurrencyIT extends AbstractIntegrationTest {

  /** Fälligkeit weit jenseits jedes Worker-Laufs — nur dieser Test greift den Eintrag. */
  private static final Duration FAR_FUTURE = Duration.ofDays(365);

  @Autowired private OutboxRepository repository;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  @Test
  void secondWorkerSkipsAnEntryAlreadyBeingProcessed() throws InterruptedException {
    // Given — direkt eingefügt statt über den Schreibweg: der braucht eine laufende
    // Fachtransaktion (MANDATORY) und ist im OutboxIT bereits geprüft.
    Instant dueAt = Instant.now().plus(FAR_FUTURE);
    jdbc.update(
        "INSERT INTO outbox_entry"
            + " (event_type, idempotency_key, payload, status, attempts, created_at,"
            + " next_attempt_at)"
            + " VALUES ('test.effect', 'race:1', 'payload', 'PENDING', 0, now(), ?)",
        Timestamp.from(dueAt));
    long id = jdbc.queryForObject("SELECT id FROM outbox_entry", Long.class);
    Instant claimAt = dueAt.plus(Duration.ofDays(1));
    AtomicBoolean secondClaimedIt = new AtomicBoolean(true);

    // When — A hält den Eintrag gesperrt, B versucht ihn währenddessen zu greifen.
    TransactionRace.Result race =
        new TransactionRace(transactionManager, dataSource)
            .runUnblocked(
                () -> repository.claimDue(id, claimAt),
                () -> secondClaimedIt.set(repository.claimDue(id, claimAt).isPresent()));

    // Then
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    assertThat(secondClaimedIt).isFalse();
  }
}
