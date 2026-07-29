package org.mwolff.manban.outbox.domain;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;

/**
 * Ein vorgemerkter Seiteneffekt: das Vorhaben, nach dem Commit der fachlichen Transaktion etwas
 * außerhalb der Datenbank zu tun (Mail versenden, Objekt im Speicher löschen) — Issue #501.
 *
 * <p><strong>Warum die Payload in einem Endzustand geleert wird:</strong> Der Vertrag am
 * Schreibport verlangt, dass die Payload nur <em>Referenzen</em> enthält und keine Geheimnisse
 * (Verifikations- und Reset-Links tragen Klartext-Tokens). Das Leeren beim Erreichen von {@link
 * OutboxStatus#DONE} oder {@link OutboxStatus#FAILED} ist die Tiefenverteidigung dahinter: Selbst
 * wenn ein Handler den Vertrag verletzt, überlebt der Inhalt die Zustellung nicht und liegt nicht
 * dauerhaft in der Tabelle. Der Eintrag bleibt vollständig auffindbar (Typ, Idempotenzschlüssel,
 * Versuche, Fehler) — nur der Inhalt ist fort. Ein {@code FAILED}-Eintrag lässt sich deshalb
 * bewusst <em>nicht</em> durch bloßes Zurücksetzen wiederholen; eine Wiederholung heißt, das
 * fachliche Ereignis neu einzuplanen. Das ist der Preis dafür, dass die Tabelle kein Endlager für
 * Klartext-Geheimnisse wird (CLAUDE.md: Sicherheit vor Bequemlichkeit).
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param eventType fachlicher Ereignistyp; wählt den zuständigen Handler
 * @param idempotencyKey stabiler Schlüssel des fachlichen Ereignisses (in der DB eindeutig)
 * @param payload Referenzen für den Handler; in einem Endzustand leer (siehe oben)
 * @param status aktueller Zustand
 * @param attempts Zahl der bereits unternommenen Versuche
 * @param createdAt Zeitpunkt der Einplanung
 * @param nextAttemptAt frühester Zeitpunkt des nächsten Versuchs
 * @param completedAt Zeitpunkt des Endzustands; {@code null} solange offen
 * @param lastError Fehlertext des letzten Fehlversuchs; {@code null} wenn noch keiner auftrat
 */
public record OutboxEntry(
    @Nullable Long id,
    String eventType,
    String idempotencyKey,
    String payload,
    OutboxStatus status,
    int attempts,
    Instant createdAt,
    Instant nextAttemptAt,
    @Nullable Instant completedAt,
    @Nullable String lastError)
    implements Identifiable {

  /** Spaltenbreite von {@code last_error}; längere Fehlertexte werden abgeschnitten. */
  public static final int MAX_ERROR_LENGTH = 1000;

  /**
   * Obergrenze der Verschiebung im exponentiellen Backoff. Ohne sie würde {@code 1L << attempts} ab
   * 64 Versuchen umlaufen und plötzlich kurze statt langer Wartezeiten liefern.
   */
  private static final int MAX_BACKOFF_SHIFT = 30;

  /** Frisch eingeplantes, sofort fälliges Vorhaben. */
  public static OutboxEntry pending(
      String eventType, String idempotencyKey, String payload, Instant now) {
    return new OutboxEntry(
        null, eventType, idempotencyKey, payload, OutboxStatus.PENDING, 0, now, now, null, null);
  }

  /** Erfolgreich zugestellt: zählt den Versuch mit und gibt Payload wie Fehlertext frei. */
  public OutboxEntry completed(Instant now) {
    return new OutboxEntry(
        id,
        eventType,
        idempotencyKey,
        "",
        OutboxStatus.DONE,
        attempts + 1,
        createdAt,
        nextAttemptAt,
        now,
        null);
  }

  /**
   * Ergebnis eines Fehlversuchs: entweder ein erneuter Versuch mit wachsendem Abstand oder — wenn
   * {@code maxAttempts} erreicht ist — die endgültige Aufgabe.
   *
   * @param now aktueller Zeitpunkt
   * @param maxAttempts Zahl der Versuche, nach der aufgegeben wird
   * @param baseDelay Wartezeit vor dem zweiten Versuch (verdoppelt sich danach je Versuch)
   * @param maxDelay Obergrenze der Wartezeit
   * @param error Fehlertext; wird auf {@link #MAX_ERROR_LENGTH} gekürzt
   */
  public OutboxEntry afterFailedAttempt(
      Instant now, int maxAttempts, Duration baseDelay, Duration maxDelay, String error) {
    int nextAttempts = attempts + 1;
    // Kürzen bewusst über Math.min statt über ein Ternär mit >-Vergleich: bei exakt passender
    // Länge lieferten beide Zweige dasselbe, der Grenzwert-Mutant wäre also nicht tötbar.
    String recordedError = error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH));
    if (nextAttempts >= maxAttempts) {
      return new OutboxEntry(
          id,
          eventType,
          idempotencyKey,
          "",
          OutboxStatus.FAILED,
          nextAttempts,
          createdAt,
          now,
          now,
          recordedError);
    }
    return new OutboxEntry(
        id,
        eventType,
        idempotencyKey,
        payload,
        OutboxStatus.PENDING,
        nextAttempts,
        createdAt,
        now.plus(backoff(nextAttempts, baseDelay, maxDelay)),
        null,
        recordedError);
  }

  /**
   * Wartezeit vor dem {@code attempt}-ten Versuch: {@code baseDelay * 2^(attempt-1)}, gedeckelt auf
   * {@code maxDelay}.
   *
   * <p>Gerechnet wird in Millisekunden — feiner muss ein Retry-Abstand nicht sein, und {@link
   * Math#min} deckelt ohne Verzweigung (ein Ternär mit {@code >}-Vergleich lieferte bei Gleichstand
   * in beiden Zweigen dasselbe, der Grenzwert-Mutant wäre nicht tötbar). Der Shift-Deckel hält das
   * Produkt zugleich weit im {@code long}-Bereich: selbst eine Basis von einer Stunde bleibt bei
   * {@code 2^30} rund drei Zehnerpotenzen unter dem Überlauf.
   */
  private static Duration backoff(int attempt, Duration baseDelay, Duration maxDelay) {
    long factor = 1L << Math.min(attempt - 1, MAX_BACKOFF_SHIFT);
    return Duration.ofMillis(Math.min(baseDelay.toMillis() * factor, maxDelay.toMillis()));
  }
}
