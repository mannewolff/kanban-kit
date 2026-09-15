package org.mwolff.manban.ratelimit.application;

import org.jspecify.annotations.Nullable;
import org.mwolff.manban.ratelimit.domain.OriginAttempts;

/**
 * Port auf den Zählzustand der Bremse (Plan #892, E6).
 *
 * <p>Der Zustand liegt im Prozessspeicher, nicht in der Datenbank: Ein Schreibzugriff je
 * Anmeldeversuch machte die Bremse selbst zum Lastvektor — genau das, wogegen sie schützt. Dass der
 * Zugriff trotzdem über einen Port läuft, hält die Tür für einen persistenten Adapter offen, falls
 * die Anwendung je mehrfach läuft (E12).
 */
public interface AttemptStore {

  /**
   * Schlüssel eines Zähleintrags: Gezählt wird je Herkunft <em>und</em> Vorgang, damit ein
   * ausgereizter Anmeldezähler nicht auch die Registrierung derselben Herkunft sperrt.
   *
   * @param origin die Herkunft des Aufrufs (IPv4-Adresse oder IPv6-{@code /64}-Präfix)
   * @param operation der begrenzte Vorgang
   */
  record Key(String origin, RateLimitedOperation operation) {}

  /** Der Zustand zum Schlüssel; {@code null}, wenn zu dieser Herkunft nichts gezählt ist. */
  @Nullable OriginAttempts get(Key key);

  /** Schreibt den Zustand zum Schlüssel. */
  void put(Key key, OriginAttempts attempts);

  /** Verwirft den Zustand zum Schlüssel. */
  void remove(Key key);
}
