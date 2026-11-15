package org.mwolff.manban.card.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Vorgabe eines Zeitpunkts für die laufende Transaktion (Issue #2159, Plan #2152 A4): Wer einen
 * Spalten- oder Statuswechsel mit einem Zeitpunkt von außen bucht — etwa dem, den ein fremdes Board
 * für den Wechsel festhält —, setzt ihn hier, bevor er schreibt. Spaltenverlauf ({@link
 * CardMoveService}, {@link KartenGrundlage#anlegen}) und Flusserfassung stempeln dann mit der
 * Vorgabe statt mit der Uhr.
 *
 * <p>Die Vorgabe hängt an der Transaktion wie die Sammlung der Flusserfassung: gesetzt nur in einer
 * laufenden, nach ihrem Ende (Commit oder Rollback) wieder leer. Eine ausgesetzte Transaktion nimmt
 * ihre Vorgabe mit; eine darin gestartete neue sieht sie nicht. Ohne Vorgabe gilt die Uhr — jedes
 * Verhalten bleibt dann, wie es war.
 */
public final class FlussZeitpunkt {

  private FlussZeitpunkt() {}

  /**
   * Setzt den Zeitpunkt für den Rest der laufenden Transaktion; ein zweites Setzen ersetzt ihn.
   *
   * @throws IllegalStateException ohne laufende Transaktion — die Vorgabe bliebe sonst am Thread
   *     hängen
   */
  public static void setze(Instant zeitpunkt) {
    Objects.requireNonNull(zeitpunkt, "zeitpunkt");
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new IllegalStateException(
          "Ein Zeitpunkt von außen gilt nur in einer laufenden Transaktion");
    }
    Vorgabe vorgabe = gebunden();
    if (vorgabe != null) {
      vorgabe.zeitpunkt = zeitpunkt;
      return;
    }
    vorgabe = new Vorgabe(zeitpunkt);
    TransactionSynchronizationManager.registerSynchronization(vorgabe);
    TransactionSynchronizationManager.bindResource(FlussZeitpunkt.class, vorgabe);
  }

  /** Der vorgegebene Zeitpunkt der laufenden Transaktion, leer ohne Vorgabe. */
  public static Optional<Instant> vorgabe() {
    Vorgabe vorgabe = gebunden();
    return vorgabe == null ? Optional.empty() : Optional.of(vorgabe.zeitpunkt);
  }

  /** Die Vorgabe der laufenden Transaktion, ohne sie der Zeitpunkt der Uhr. */
  public static Instant jetzt(Clock uhr) {
    return vorgabe().orElseGet(uhr::instant);
  }

  private static @Nullable Vorgabe gebunden() {
    return (Vorgabe) TransactionSynchronizationManager.getResource(FlussZeitpunkt.class);
  }

  /** Die Vorgabe einer Transaktion; räumt sich mit ihrem Ende ab. */
  private static final class Vorgabe implements TransactionSynchronization {

    private Instant zeitpunkt;

    Vorgabe(Instant zeitpunkt) {
      this.zeitpunkt = zeitpunkt;
    }

    @Override
    public void suspend() {
      TransactionSynchronizationManager.unbindResource(FlussZeitpunkt.class);
    }

    @Override
    public void resume() {
      TransactionSynchronizationManager.bindResource(FlussZeitpunkt.class, this);
    }

    @Override
    public void afterCompletion(int status) {
      TransactionSynchronizationManager.unbindResourceIfPossible(FlussZeitpunkt.class);
    }
  }
}
