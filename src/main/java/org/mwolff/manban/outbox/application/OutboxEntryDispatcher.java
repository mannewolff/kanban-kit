package org.mwolff.manban.outbox.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.mwolff.manban.outbox.domain.OutboxEntry;
import org.mwolff.manban.outbox.domain.OutboxStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arbeitet genau einen Outbox-Eintrag ab — je Eintrag eine eigene Transaktion (Issue #501).
 *
 * <p><strong>Warum je Eintrag und nicht je Durchlauf:</strong> Ein einzelner scheiternder
 * Seiteneffekt darf die erfolgreichen Nachbarn desselben Durchlaufs nicht mit zurückrollen. Der
 * Aufrufer ({@link OutboxDispatchService}) ist deshalb ein eigener Bean — ein Selbstaufruf käme am
 * Transaktions-Proxy vorbei und die Transaktionsgrenze wäre stillschweigend keine.
 *
 * <p><strong>Warum der Seiteneffekt innerhalb der Sperre läuft:</strong> Er hält die Zeilensperre
 * für seine Dauer, und genau das macht ihn gegen einen zweiten Worker dicht — dieser überspringt
 * den Eintrag, statt ihn parallel ein zweites Mal auszuführen. Der Preis ist eine offene
 * Transaktion für die Dauer des Aufrufs; deshalb gilt der {@link OutboxHandler}-Vertrag, dass
 * Handler nur äußere Systeme mit harten Zeitgrenzen ansprechen.
 *
 * <p>Zugestellt wird <strong>mindestens einmal</strong>: Ein Absturz zwischen Seiteneffekt und
 * Commit lässt den Eintrag offen, der nächste Lauf versucht es erneut. Dass daraus keine doppelte
 * Wirkung wird, ist Sache des Handlers.
 */
// PMD.AvoidCatchingGenericException: Ein Outbox-Worker muss jeden Fehler eines fremden Handlers
// überleben — genau dafür existiert der Wiederholungszähler. Fängt er nur geprüfte Ausnahmen, reißt
// die erste unerwartete RuntimeException eines Handlers den ganzen Durchlauf mit, und die
// Nachbareinträge bleiben liegen. Der Fang ist hier also die Fehlerbehandlung selbst, nicht ihr
// Verlust: Der Eintrag wird als Fehlversuch verbucht und später erneut versucht.
@SuppressWarnings("PMD.AvoidCatchingGenericException")
@Service
public class OutboxEntryDispatcher {

  private static final Logger log = LoggerFactory.getLogger(OutboxEntryDispatcher.class);

  private final Map<String, OutboxHandler> handlers;
  private final OutboxRepository repository;
  private final OutboxProperties properties;
  private final Clock clock;

  public OutboxEntryDispatcher(
      List<OutboxHandler> handlers,
      OutboxRepository repository,
      OutboxProperties properties,
      Clock clock) {
    // Zwei Handler für denselben Ereignistyp wären ein stiller Zufallsentscheid, welcher gewinnt —
    // deshalb bricht das Hochfahren ab, statt die Wahl dem Bean-Scan zu überlassen.
    this.handlers =
        handlers.stream()
            .collect(
                Collectors.toMap(
                    OutboxHandler::eventType,
                    Function.identity(),
                    (first, second) -> {
                      throw new IllegalStateException(
                          "Mehr als ein Outbox-Handler für den Ereignistyp " + first.eventType());
                    }));
    this.repository = repository;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Sperrt den Eintrag, führt seinen Seiteneffekt aus und schreibt das Ergebnis fort.
   *
   * @return {@code true}, wenn der Seiteneffekt gelang; {@code false}, wenn der Eintrag nicht mehr
   *     zu greifen war oder der Versuch fehlschlug
   */
  @Transactional
  public boolean dispatch(long id) {
    Instant now = clock.instant();
    Optional<OutboxEntry> claimed = repository.claimDue(id, now);
    if (claimed.isEmpty()) {
      return false;
    }
    OutboxEntry entry = claimed.get();
    try {
      handlerFor(entry.eventType()).handle(entry.payload());
    } catch (RuntimeException e) {
      recordFailedAttempt(entry, now, e);
      return false;
    }
    repository.update(entry.completed(now));
    return true;
  }

  private OutboxHandler handlerFor(String eventType) {
    OutboxHandler handler = handlers.get(eventType);
    if (handler == null) {
      // Kein Sonderfall, sondern ein Fehlversuch: Nach einem Rollout, bei dem der Handler noch
      // fehlt, soll der Eintrag später von selbst durchlaufen statt sofort verloren zu sein.
      throw new IllegalStateException("Kein Outbox-Handler für den Ereignistyp " + eventType);
    }
    return handler;
  }

  private void recordFailedAttempt(OutboxEntry entry, Instant now, RuntimeException cause) {
    OutboxEntry next =
        entry.afterFailedAttempt(
            now,
            properties.maxAttempts(),
            properties.retryBaseDelay(),
            properties.retryMaxDelay(),
            String.valueOf(cause));
    repository.update(next);
    if (next.status() == OutboxStatus.FAILED) {
      log.error(
          "Outbox-Eintrag {} ({}, Schlüssel {}) nach {} Versuchen endgültig gescheitert",
          entry.requireId(),
          entry.eventType(),
          entry.idempotencyKey(),
          next.attempts(),
          cause);
    } else {
      log.warn(
          "Outbox-Eintrag {} ({}) fehlgeschlagen, Versuch {} um {}",
          entry.requireId(),
          entry.eventType(),
          next.attempts(),
          next.nextAttemptAt(),
          cause);
    }
  }
}
