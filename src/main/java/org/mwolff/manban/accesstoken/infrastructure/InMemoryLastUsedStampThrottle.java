package org.mwolff.manban.accesstoken.infrastructure;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.application.LastUsedStampThrottle;
import org.springframework.stereotype.Component;

/**
 * Drosselungsspeicher im Prozessspeicher (Issue #997, Plan #995 E6).
 *
 * <p><strong>Warum im Prozessspeicher:</strong> Die Entscheidung „schreiben oder nicht" fällt bei
 * jedem API-Aufruf. Käme sie aus der Datenbank, kostete sie genau den Zugriff, den sie einsparen
 * soll. Mehrere Instanzen stempeln dadurch je Instanz einmal pro Minute — für die Aussage „zuletzt
 * benutzt" ist das ohne Belang.
 *
 * <p><strong>Warum eine Obergrenze:</strong> Der Speicher wüchse sonst mit der Zahl der je
 * gesehenen Token — jeder abgewiesene Rateversuch eines Angreifers legte keinen Eintrag an (der
 * Stempel entsteht erst nach erfolgreicher Auflösung), ein Nutzer mit vielen Token aber schon. Beim
 * Schreiben auf eine volle Tabelle verschwinden zuerst die Einträge, deren Minute ohnehin um ist;
 * reicht das nicht, wird ein <em>beliebiger</em> Eintrag verdrängt. Zugesagt ist die Obergrenze,
 * nicht die Reihenfolge — dieselbe Abwägung wie in {@code InMemoryAttemptStore}, und ein
 * verdrängter Eintrag kostet höchstens einen zusätzlichen Schreibvorgang.
 */
@Component
public class InMemoryLastUsedStampThrottle implements LastUsedStampThrottle {

  /** Höchstens ein Stempel je Token und Minute — die fachliche Genauigkeit aus #997. */
  private static final Duration INTERVAL = Duration.ofMinutes(1);

  /** Obergrenze der gleichzeitig verfolgten Token; bewusst ohne Stellschraube (kein AK dafür). */
  private static final int DEFAULT_MAX_TRACKED_TOKENS = 100_000;

  private final Map<Long, Instant> stamps = new ConcurrentHashMap<>();
  private final int maxTrackedTokens;

  public InMemoryLastUsedStampThrottle() {
    this(DEFAULT_MAX_TRACKED_TOKENS);
  }

  InMemoryLastUsedStampThrottle(int maxTrackedTokens) {
    this.maxTrackedTokens = maxTrackedTokens;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Prüfen und Vermerken fallen in <em>einem</em> {@link ConcurrentHashMap#compute}-Aufruf
   * zusammen: Getrennt (erst lesen, dann schreiben) kämen zehn gleichzeitige Befehle alle am leeren
   * Eintrag vorbei und nähmen alle denselben Zeilen-Lock — also genau die Serialisierung, gegen die
   * diese Klasse antritt.
   */
  @Override
  public boolean claimStamp(long tokenId, Instant now) {
    AtomicBoolean claimed = new AtomicBoolean();
    makeRoomIfNeeded(tokenId, now);
    stamps.compute(
        tokenId,
        (id, last) -> {
          if (isDue(last, now)) {
            claimed.set(true);
            return now;
          }
          return last;
        });
    return claimed.get();
  }

  /** Die Zahl der aktuell verfolgten Token — sichtbar für den Nachweis der Obergrenze. */
  int tracked() {
    return stamps.size();
  }

  /** Ob seit dem letzten Stempel mindestens eine Minute vergangen ist (oder es keinen gibt). */
  private static boolean isDue(@Nullable Instant last, Instant now) {
    return last == null || !now.isBefore(last.plus(INTERVAL));
  }

  /**
   * Schafft Platz für genau einen weiteren Eintrag: erst Abgelaufenes, dann Beliebiges. Bewusst nur
   * bei voller Tabelle und unbekanntem Token — ein Ablauf-Scan bei jedem API-Aufruf wäre genau der
   * Aufwand, den die Drosselung einspart.
   */
  private void makeRoomIfNeeded(long tokenId, Instant now) {
    if (stamps.size() < maxTrackedTokens || stamps.containsKey(tokenId)) {
      return;
    }
    stamps.entrySet().removeIf(entry -> isDue(entry.getValue(), now));
    long surplus = stamps.size() - maxTrackedTokens + 1L;
    stamps.keySet().stream().limit(Math.max(surplus, 0L)).toList().forEach(stamps::remove);
  }
}
