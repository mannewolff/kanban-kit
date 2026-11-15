package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Die transaktionsgebundene Vorgabe eines Zeitpunkts (Issue #2159, Plan #2152 A4): gesetzt nur in
 * einer Transaktion, gelesen in derselben, nach ihrem Ende leer.
 */
class FlussZeitpunktTest {

  private static final Instant UHRZEIT = Instant.parse("2026-01-02T03:04:05Z");
  private static final Instant VORGABE = Instant.parse("2025-12-24T18:00:00Z");
  private static final Clock UHR = Clock.fixed(UHRZEIT, ZoneOffset.UTC);

  @AfterEach
  void aufraeumen() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
    TransactionSynchronizationManager.unbindResourceIfPossible(FlussZeitpunkt.class);
  }

  @Test
  void ohneVorgabe_istSieLeer_undJetztKommtVonDerUhr() {
    assertThat(FlussZeitpunkt.vorgabe()).isEmpty();
    assertThat(FlussZeitpunkt.jetzt(UHR)).isEqualTo(UHRZEIT);
  }

  @Test
  void setzenOhneTransaktion_wirdAbgewiesen() {
    assertThatThrownBy(() -> FlussZeitpunkt.setze(VORGABE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Transaktion");
    assertThat(FlussZeitpunkt.vorgabe()).isEmpty();
  }

  @Test
  void inDerTransaktion_giltDieVorgabe() {
    TransactionSynchronizationManager.initSynchronization();

    FlussZeitpunkt.setze(VORGABE);

    assertThat(FlussZeitpunkt.vorgabe()).contains(VORGABE);
    assertThat(FlussZeitpunkt.jetzt(UHR)).isEqualTo(VORGABE);
  }

  @Test
  void zweitesSetzen_ersetztDieVorgabe_ohneZweiteSynchronisation() {
    TransactionSynchronizationManager.initSynchronization();
    Instant spaeter = VORGABE.plusSeconds(60);

    FlussZeitpunkt.setze(VORGABE);
    FlussZeitpunkt.setze(spaeter);

    assertThat(FlussZeitpunkt.vorgabe()).contains(spaeter);
    assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
  }

  @Test
  void nachDemTransaktionsende_istSieLeer() {
    TransactionSynchronizationManager.initSynchronization();
    FlussZeitpunkt.setze(VORGABE);

    TransactionSynchronizationManager.getSynchronizations()
        .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

    assertThat(FlussZeitpunkt.vorgabe()).isEmpty();
    assertThat(FlussZeitpunkt.jetzt(UHR)).isEqualTo(UHRZEIT);
  }

  @Test
  void eineAusgesetzteTransaktion_siehtDieVorgabeNicht_bisSieWeiterlaeuft() {
    TransactionSynchronizationManager.initSynchronization();
    FlussZeitpunkt.setze(VORGABE);
    TransactionSynchronization synchronisation =
        TransactionSynchronizationManager.getSynchronizations().getFirst();

    synchronisation.suspend();
    assertThat(FlussZeitpunkt.vorgabe()).isEmpty();

    synchronisation.resume();
    assertThat(FlussZeitpunkt.vorgabe()).contains(VORGABE);
  }
}
