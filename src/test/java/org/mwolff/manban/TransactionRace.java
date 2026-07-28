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
 * <p>{@link #runUnblocked} ist die Gegenrichtung: Dort muss der zweite Aufruf <em>durchlaufen</em>,
 * während der erste seine Sperren hält — damit lässt sich nachweisen, dass eine Sperre
 * <em>nicht</em> weiter greift als nötig (Issue #499: verschiedene Projekte bzw. Spalten dürfen
 * sich nicht gegenseitig ausbremsen).
 *
 * <p>Die Klasse wird von mehreren Nebenläufigkeits-ITs genutzt (Einmal-Tokens, Issue #497;
 * Rollen-Invarianten, Issue #498; Nummern und Positionen, Issue #499). Sie gehört bewusst in den
 * Testbaum und nicht in die Produktion: Sie stellt keine Nebenläufigkeits-Primitive bereit, sondern
 * weist eine nach.
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

  /** Was zwischen dem Abschluss von {@code first} und dessen Commit abgewartet wird. */
  @FunctionalInterface
  private interface Await {
    void await(Thread secondThread) throws InterruptedException;
  }

  /**
   * Führt {@code first} und {@code second} in zwei echten Transaktionen so aus, dass {@code second}
   * garantiert auf einer von {@code first} gehaltenen Sperre wartet, bevor {@code first} committet.
   */
  public Result run(Runnable first, Runnable second) throws InterruptedException {
    return race(first, second, secondThread -> awaitBlockedOnLock());
  }

  /**
   * Gegenrichtung zu {@link #run}: {@code second} muss vollständig durchlaufen, <em>während</em>
   * {@code first} seine Transaktion — und damit seine Sperren — noch offen hält.
   *
   * <p>Damit wird nachgewiesen, dass eine Sperre nicht weiter greift als nötig: Wartet {@code
   * second} doch, endet der Lauf mit {@link IllegalStateException} statt mit einem stillen
   * Durchlauf. Das ist das Akzeptanzkriterium „parallele Änderungen verschiedener Projekte bzw.
   * Spalten blockieren sich nicht gegenseitig" (Issue #499) als ausführbare Aussage.
   */
  public Result runUnblocked(Runnable first, Runnable second) throws InterruptedException {
    return race(first, second, TransactionRace::requireFinishedWhileFirstHoldsLocks);
  }

  private Result race(Runnable first, Runnable second, Await awaitSecond)
      throws InterruptedException {
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
      awaitSecond.await(secondThread);
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

  /**
   * Wartet, bis die zweite Transaktion fertig ist — und besteht darauf, dass sie das schafft,
   * solange die erste ihre Sperren noch hält. Bleibt sie hängen, greift eine Sperre weiter als
   * nötig.
   */
  private static void requireFinishedWhileFirstHoldsLocks(Thread secondThread)
      throws InterruptedException {
    secondThread.join(TimeUnit.SECONDS.toMillis(DEADLINE_SECONDS));
    if (secondThread.isAlive()) {
      throw new IllegalStateException(
          "Die zweite Transaktion wurde blockiert, obwohl sie eine andere Ressource betrifft — die"
              + " Sperre greift weiter als nötig.");
    }
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
