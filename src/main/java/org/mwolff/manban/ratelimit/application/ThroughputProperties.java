package org.mwolff.manban.ratelimit.application;

import org.mwolff.manban.ratelimit.domain.PersonBudget;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguration der Durchsatzbremse (Issue #999, Plan #995 E3). Wrapper-Typen statt Primitiven wie
 * in {@link RateLimitProperties}, damit „nicht gesetzt" ({@code null}) vom Vorgabewert
 * unterscheidbar bleibt.
 *
 * <p>Ein eigener Typ neben {@link RateLimitProperties}, obwohl beide unter {@code manban.ratelimit}
 * stehen: Die Auth-Bremse sperrt nach N Fehlversuchen für eine feste Dauer, diese Bremse füllt
 * kontinuierlich nach und weist nur den Überschuss ab. Ein gemeinsamer Typ trüge zwei Bedeutungen
 * für dieselben Felder (E2).
 *
 * <p><strong>Modulintern</strong> wie {@link RateLimitProperties}: Konfiguration ist kein
 * Vertragsbestandteil. Die ArchUnit-Whitelist des Moduls lässt diesen Typ außerhalb von {@code
 * ratelimit} nicht zu; wer wissen will, ob die Bremse läuft, fragt {@link
 * ThroughputLimiter#isEnabled()}.
 *
 * @param enabled ob die Bremse greift; abgeschaltet wird nichts gezählt und nichts abgewiesen
 * @param perMinute Befehle je Person und Minute (Nachfüllrate und Eimergröße)
 * @param concurrent Deckel der gleichzeitig laufenden Befehle einer Person
 * @param maxTrackedPersons Obergrenze der gleichzeitig verfolgten Personen
 */
@ConfigurationProperties(prefix = "manban.ratelimit.throughput")
public record ThroughputProperties(
    Boolean enabled, Integer perMinute, Integer concurrent, Integer maxTrackedPersons) {

  private static final int DEFAULT_PER_MINUTE = 60;
  private static final int DEFAULT_CONCURRENT = 10;
  private static final int DEFAULT_MAX_TRACKED_PERSONS = 100_000;

  public ThroughputProperties {
    if (enabled == null) {
      enabled = Boolean.TRUE;
    }
    if (perMinute == null || perMinute < 1) {
      perMinute = DEFAULT_PER_MINUTE;
    }
    if (concurrent == null || concurrent < 1) {
      concurrent = DEFAULT_CONCURRENT;
    }
    if (maxTrackedPersons == null || maxTrackedPersons < 1) {
      maxTrackedPersons = DEFAULT_MAX_TRACKED_PERSONS;
    }
  }

  /** Die Grenzwerte in der Form, die die Domäne verarbeitet. */
  public PersonBudget.Limits limits() {
    return PersonBudget.Limits.of(perMinute, concurrent);
  }
}
