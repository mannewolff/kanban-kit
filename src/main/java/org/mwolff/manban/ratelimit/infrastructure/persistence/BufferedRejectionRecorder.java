package org.mwolff.manban.ratelimit.infrastructure.persistence;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.mwolff.manban.ratelimit.application.RejectionRecorder;
import org.mwolff.manban.ratelimit.infrastructure.persistence.OverloadRejectionTable.HourKey;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nimmt Abweisungen im Arbeitsspeicher entgegen und schreibt sie gebündelt fort (Issue #1000, Plan
 * #995 E14).
 *
 * <p><strong>Warum gepuffert:</strong> Abweisungen häufen sich genau dann, wenn das System unter
 * Last steht. Ein Schreibzugriff je Abweisung machte die Meldung selbst zum Lastvektor. Der Puffer
 * zählt je Person und voller Stunde; ein {@link #flush()} schreibt alles Aufgelaufene in einem
 * Batch, gleich wie viele Abweisungen es waren.
 *
 * <p><strong>Was bei einem Fehler geschieht:</strong> Scheitert das Schreiben, wandern die
 * Zählungen zurück in den Puffer und gehen mit dem nächsten Lauf hinaus. Bei einem harten Absturz
 * gehen höchstens die Zählungen eines Puffer-Fensters verloren — für eine Übersicht „wann und bei
 * wem" ist das hinnehmbar, ein Schreibzugriff je Abweisung wäre es nicht.
 */
@Component
public class BufferedRejectionRecorder implements RejectionRecorder {

  private final Map<HourKey, Integer> buffer = new ConcurrentHashMap<>();
  private final OverloadRejectionTable table;
  private final Clock clock;

  public BufferedRejectionRecorder(OverloadRejectionTable table, Clock clock) {
    this.table = table;
    this.clock = clock;
  }

  @Override
  public void record(long userId) {
    buffer.merge(
        new HourKey(userId, clock.instant().truncatedTo(ChronoUnit.HOURS)), 1, Integer::sum);
  }

  /**
   * Schreibt alles Aufgelaufene in einem Batch. Jeder Eintrag wird einzeln aus dem Puffer genommen:
   * Eine Abweisung, die währenddessen hinzukommt, landet entweder in diesem Batch oder im nächsten,
   * aber nie in keinem.
   *
   * <p>{@code synchronized}, weil der geplante Lauf und der beim Herunterfahren sich überschneiden
   * können. Nur ein Flush entfernt Schlüssel, {@link #record} fügt sie bloß hinzu; mit dem Monitor
   * ist jeder Schlüssel der Kopie beim Entfernen also noch vorhanden.
   */
  @Scheduled(fixedDelayString = "${manban.ratelimit.throughput.rejection-flush-ms:10000}")
  public synchronized void flush() {
    Map<HourKey, Integer> batch = new HashMap<>();
    for (HourKey key : List.copyOf(buffer.keySet())) {
      batch.put(key, Objects.requireNonNull(buffer.remove(key)));
    }
    if (batch.isEmpty()) {
      return;
    }
    try {
      table.add(batch);
    } catch (DataAccessException e) {
      batch.forEach((key, count) -> buffer.merge(key, count, Integer::sum));
      throw e;
    }
  }

  /** Beim Herunterfahren geht der Puffer noch hinaus, statt mit dem Prozess zu verschwinden. */
  @PreDestroy
  void flushOnShutdown() {
    flush();
  }
}
