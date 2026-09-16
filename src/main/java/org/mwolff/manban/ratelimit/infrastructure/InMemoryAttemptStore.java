package org.mwolff.manban.ratelimit.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.ratelimit.application.AttemptStore;
import org.mwolff.manban.ratelimit.application.RateLimitProperties;
import org.mwolff.manban.ratelimit.domain.OriginAttempts;
import org.springframework.stereotype.Component;

/**
 * Zählspeicher im Prozessspeicher (Plan #892, E6/E7).
 *
 * <p><strong>Warum eine Obergrenze:</strong> Ein Angreifer mit einem IPv6-Präfix erzeugt beliebig
 * viele Herkünfte; ohne Deckel flutete er die Tabelle, bis der Prozess am Speicher stirbt — die
 * Bremse wäre selbst die Waffe. Beim Schreiben auf eine volle Tabelle verschwinden zuerst die
 * abgelaufenen Einträge; reicht das nicht, wird ein <em>beliebiger</em> Eintrag verdrängt. Zugesagt
 * ist die Obergrenze, nicht die Reihenfolge: „der älteste Eintrag" wäre über eine {@link
 * ConcurrentHashMap} nur mit zusätzlicher Buchführung oder einem Scan über hunderttausend Einträge
 * zu haben, und beides würde im Flutungsfall selbst zum Lastvektor.
 *
 * <p><strong>Warum erst bei voller Tabelle geräumt wird:</strong> Ein Ablauf-Scan bei jedem
 * Schreibzugriff — also bei jedem Anmeldeversuch — wäre genau der Aufwand, den E6 vermeiden will.
 * Die Korrektheit hängt nicht daran: Ein abgelaufener Eintrag ist kein falscher Zustand, denn
 * {@link OriginAttempts} setzt ein verstrichenes Fenster und eine abgelaufene Sperre beim nächsten
 * Zugriff ohnehin zurück. Geräumt wird also nur, wo es um Speicher geht.
 */
@Component
public class InMemoryAttemptStore implements AttemptStore {

  private final Map<Key, OriginAttempts> entries = new ConcurrentHashMap<>();
  private final RateLimitProperties properties;
  private final Clock clock;

  public InMemoryAttemptStore(RateLimitProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public @Nullable OriginAttempts get(Key key) {
    return entries.get(key);
  }

  @Override
  public void put(Key key, OriginAttempts attempts) {
    if (entries.size() >= properties.maxTrackedOrigins() && !entries.containsKey(key)) {
      makeRoom(clock.instant());
    }
    entries.put(key, attempts);
  }

  @Override
  public void remove(Key key) {
    entries.remove(key);
  }

  /** Schafft Platz für genau einen weiteren Eintrag: erst Abgelaufenes, dann Beliebiges. */
  private void makeRoom(Instant now) {
    entries.entrySet().removeIf(entry -> isExpired(entry.getKey(), entry.getValue(), now));
    long surplus = entries.size() - properties.maxTrackedOrigins() + 1L;
    entries.keySet().stream().limit(Math.max(surplus, 0L)).toList().forEach(entries::remove);
  }

  /**
   * Ob der Eintrag nichts mehr aussagt — geprüft nach denselben Zeitregeln, nach denen {@link
   * OriginAttempts} ihn beim nächsten Zugriff zurücksetzen würde. Eine laufende Sperre ist der
   * Zweck des Speichers und bleibt; eine abgelaufene wie ein verstrichenes Zählfenster ist Ballast.
   */
  private boolean isExpired(Key key, OriginAttempts attempts, Instant now) {
    Instant blockedUntil = attempts.blockedUntil();
    if (blockedUntil != null) {
      return !now.isBefore(blockedUntil);
    }
    Instant windowEnd =
        attempts.windowStartedAt().plus(properties.limitsFor(key.operation()).window());
    return !now.isBefore(windowEnd);
  }
}
