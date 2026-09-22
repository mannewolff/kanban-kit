package org.mwolff.manban.ratelimit.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.ratelimit.application.PersonBudgetStore;
import org.mwolff.manban.ratelimit.application.ThroughputProperties;
import org.mwolff.manban.ratelimit.domain.PersonBudget;
import org.springframework.stereotype.Component;

/**
 * Zustand der Durchsatzbremse im Prozessspeicher (Issue #999, Plan #995 E4), nach dem Muster von
 * {@link InMemoryAttemptStore}.
 *
 * <p><strong>Warum eine Obergrenze:</strong> Der Speicher wüchse sonst mit jeder je gesehenen
 * Person. Beim Schreiben eines neuen Eintrags auf eine volle Tabelle verschwinden zuerst die
 * Einträge, die nichts mehr aussagen — nichts läuft, der Eimer wäre voll —; reicht das nicht, wird
 * ein <em>beliebiger</em> verdrängt. Zugesagt ist die Obergrenze, nicht die Reihenfolge. Wer
 * verdrängt wird, beginnt beim nächsten Befehl mit vollem Eimer; laufende Aufrufe, deren Zähler
 * dabei verloren geht, geben ihre Freigabe auf einen fehlenden Eintrag zurück, und das ist
 * folgenlos.
 *
 * <p><strong>Warum erst bei voller Tabelle geräumt wird:</strong> Ein Scan bei jedem Befehl wäre
 * genau der Aufwand, den der Prozessspeicher einspart. Ein ruhender Eintrag ist kein falscher
 * Zustand — {@link PersonBudget} füllt ihn beim nächsten Zugriff ohnehin nach.
 */
@Component
public class InMemoryPersonBudgetStore implements PersonBudgetStore {

  private final Map<Long, PersonBudget> entries = new ConcurrentHashMap<>();
  private final ThroughputProperties properties;
  private final Clock clock;

  public InMemoryPersonBudgetStore(ThroughputProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Die Änderung läuft in {@link ConcurrentHashMap#compute} und damit atomar je Person.
   */
  @Override
  public @Nullable PersonBudget update(long userId, UnaryOperator<@Nullable PersonBudget> change) {
    if (entries.size() >= properties.maxTrackedPersons() && !entries.containsKey(userId)) {
      makeRoom(clock.instant());
    }
    return entries.compute(userId, (id, current) -> change.apply(current));
  }

  /** Die Zahl der verfolgten Personen — sichtbar für den Nachweis der Obergrenze. */
  int tracked() {
    return entries.size();
  }

  /** Schafft Platz für genau einen weiteren Eintrag: erst Ruhendes, dann Beliebiges. */
  private void makeRoom(Instant now) {
    PersonBudget.Limits limits = properties.limits();
    entries.values().removeIf(budget -> budget.isIdle(now, limits));
    long surplus = entries.size() - properties.maxTrackedPersons() + 1L;
    entries.keySet().stream().limit(Math.max(surplus, 0L)).toList().forEach(entries::remove);
  }
}
