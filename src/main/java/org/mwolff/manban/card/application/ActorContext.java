package org.mwolff.manban.card.application;

import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.domain.CardActivityOrigin;

/**
 * Port für die Herkunft der aktuell handelnden Identität (Issue #517). Die Application-Schicht darf
 * den Spring-Sicherheitskontext nicht lesen (HTTP-Semantik); die Implementierung lebt im
 * Web-Adapter — dasselbe Muster wie {@code UserLookup} (#460).
 *
 * <p>Außerhalb eines authentifizierten Requests (Tests, interne Aufrufe) liefert der Port {@link
 * ActorStamp#unknown()} — die Aktivität wird dann wie ein Alt-Eintrag ohne Herkunft gespeichert.
 */
@FunctionalInterface
public interface ActorContext {

  /** Herkunfts-Stempel des aktuellen Aufrufs; nie {@code null}. */
  ActorStamp current();

  /**
   * Herkunfts-Stempel eines Aktivitätseintrags. {@code origin} und {@code tokenName} sind
   * server-verifiziert (Authority bzw. Token-Bindung), {@code agent} ist eine Client-Selbstauskunft
   * (Header {@code X-Agent-Model}) — sie dürfen in der Darstellung nicht gleich behandelt werden.
   */
  record ActorStamp(
      @Nullable CardActivityOrigin origin, @Nullable String tokenName, @Nullable String agent) {

    private static final ActorStamp UNKNOWN = new ActorStamp(null, null, null);

    /** Kein authentifizierter Kontext — alle Felder leer, Speicherung wie ein Alt-Eintrag. */
    public static ActorStamp unknown() {
      return UNKNOWN;
    }
  }
}
