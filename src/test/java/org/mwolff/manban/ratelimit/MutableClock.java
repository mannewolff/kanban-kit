package org.mwolff.manban.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Stellbare Uhr für die Tests der Zählbremse (Issue #897).
 *
 * <p>Eine {@code Clock.fixed} genügt hier nicht: Fassade und Speicher halten <em>eine</em>
 * Uhr-Instanz, und genau das Verstreichen von Zeit ist der Prüfgegenstand — ob die Sperre nach
 * ihrem Ende wieder freigibt, ob ein weiterer Versuch sie <em>nicht</em> verlängert, ob abgelaufene
 * Einträge beim Schreiben verschwinden. Die Uhr wird ausschließlich vom Testthread verstellt.
 */
public final class MutableClock extends Clock {

  private Instant now;

  public MutableClock(Instant start) {
    this.now = start;
  }

  /** Lässt {@code amount} Zeit verstreichen. */
  public void advance(Duration amount) {
    now = now.plus(amount);
  }

  @Override
  public Instant instant() {
    return now;
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }
}
