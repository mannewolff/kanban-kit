package org.mwolff.manban;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Führt zwei Use-Case-Aufrufe in echten, gleichzeitig offenen Transaktionen gegeneinander aus —
 * <strong>deterministisch</strong>, nicht zeitabhängig.
 *
 * <p>Der Ablauf ist über Zustände synchronisiert:
 *
 * <ol>
 *   <li>Transaktion A führt ihren Aufruf aus und hält offen — die von ihr genommenen Sperren
 *       bestehen damit weiter.
 *   <li>Transaktion B startet erst danach und läuft in genau diese Sperren.
 *   <li>{@link #run} wartet über {@code pg_stat_activity}, bis B nachweislich auf einer Sperre
 *       wartet (Zustandsabfrage, keine geschätzte Wartezeit).
 *   <li>Erst dann committet A. PostgreSQL wertet B's Bedingungen anschließend auf den neuen
 *       Zeilenversionen aus.
 * </ol>
 *
 * <p>Schritt 3 ist zugleich die Gegenprobe: Nimmt der geprüfte Code die erwartete Sperre gar nicht,
 * blockiert B nie und der Lauf endet mit {@link IllegalStateException} statt mit einer stillen
 * Zufallsreihenfolge.
 *
 * <p>Die Klasse wird von mehreren Nebenläufigkeits-ITs genutzt (Einmal-Tokens, Issue #497;
 * Rollen-Invarianten, Issue #498). Sie gehört bewusst in den Testbaum und nicht in die Produktion:
 * Sie stellt keine Nebenläufigkeits-Primitive bereit, sondern weist eine nach.
 */
public final class TransactionRace {

  /** Obergrenze für das Warten auf einen Zustand; nur Notausgang, nicht Teil der Logik. */
  private static final long DEADLINE_SECONDS = 30;

  private final PlatformTransactionManager transactionManager;
  private final DataSource dataSource;

  public TransactionRace(PlatformTransactionManager transactionManager, DataSource dataSource) {
    this.transactionManager = transactionManager;
    this.dataSource = dataSource;
  }

  /**
   * Ausgang eines Rennens: was der zuerst startende und was der zweite Aufruf geworfen hat ({@code
   * null} = erfolgreich durchgelaufen).
   */
  public record Result(@Nullable Throwable firstFailure, @Nullable Throwable secondFailure) {}

  /**
   * Führt {@code first} und {@code second} in zwei echten Transaktionen so aus, dass {@code second}
   * garantiert auf einer von {@code first} gehaltenen Sperre wartet, bevor {@code first} committet.
   */
  public Result run(Runnable first, Runnable second) throws InterruptedException {
    TransactionTemplate transactions = new TransactionTemplate(transactionManager);
    CountDownLatch firstDone = new CountDownLatch(1);
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
                      firstDone.countDown();
                      awaitLatch(firstMayCommit);
                    });
              } finally {
                firstDone.countDown(); // auch bei Fehlschlag weiterlaufen
              }
            },
            "race-first");
    firstThread.setUncaughtExceptionHandler((thread, failure) -> firstFailure.set(failure));

    Thread secondThread =
        new Thread(() -> transactions.executeWithoutResult(status -> second.run()), "race-second");
    secondThread.setUncaughtExceptionHandler((thread, failure) -> secondFailure.set(failure));

    try {
      firstThread.start();
      awaitLatch(firstDone);
      secondThread.start();
      awaitBlockedOnLock();
    } finally {
      firstMayCommit.countDown();
    }
    firstThread.join(TimeUnit.SECONDS.toMillis(DEADLINE_SECONDS));
    secondThread.join(TimeUnit.SECONDS.toMillis(DEADLINE_SECONDS));

    return new Result(firstFailure.get(), secondFailure.get());
  }

  /**
   * Wartet, bis eine Datenbanksitzung auf eine Sperre wartet. Das ist eine Zustandsabfrage: Der
   * Test schreitet erst fort, wenn die Blockade tatsächlich eingetreten ist — nicht nach Ablauf
   * einer geschätzten Wartezeit.
   */
  private void awaitBlockedOnLock() throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DEADLINE_SECONDS);
    while (System.nanoTime() < deadline) {
      if (sessionsWaitingOnLock() > 0) {
        return;
      }
      TimeUnit.MILLISECONDS.sleep(20);
    }
    throw new IllegalStateException(
        "Die zweite Transaktion wartet auf keiner Sperre — der geprüfte Ablauf ist nicht"
            + " serialisiert.");
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
}
