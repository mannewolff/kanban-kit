package org.mwolff.manban.deployment.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Ein gemeldeter Auslieferungsversuch (Issue #1832, Plan #1831 E6; Art seit Issue #2215, Plan #2206
 * E10).
 *
 * @param id interne Kennung; sie steigt mit dem Eingang und entscheidet den Gleichstand zweier
 *     Versuche mit demselben {@code finishedAt}: der zuerst eingegangene gilt als früher
 * @param attemptId die Kennung des Versuchs aus der Meldung
 * @param finishedAt Zeitpunkt des Ausgangs; er zählt, nicht der Eingang der Meldung
 * @param revision der ausgelieferte Stand (Commit)
 * @param art Vorschau oder Veröffentlichung; {@code null}, wenn die Meldung keine Art nannte — sie
 *     zählt dann als Veröffentlichung
 */
public record Auslieferungsversuch(
    long id,
    String attemptId,
    Ausgang ausgang,
    Instant finishedAt,
    String revision,
    @Nullable Auslieferungsart art) {

  /** Ein Versuch ohne gemeldete Art, also eine Veröffentlichung. */
  public Auslieferungsversuch(
      long id, String attemptId, Ausgang ausgang, Instant finishedAt, String revision) {
    this(id, attemptId, ausgang, finishedAt, revision, null);
  }

  /** Ob der Versuch eine Vorschau ist; nur sie bleibt aus Kennzahlen und letztem Stand heraus. */
  public boolean istVorschau() {
    return art == Auslieferungsart.VORSCHAU;
  }
}
