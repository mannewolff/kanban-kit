package org.mwolff.manban.ratelimit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.ratelimit.MutableClock;
import org.mwolff.manban.ratelimit.application.ThroughputProperties;
import org.mwolff.manban.ratelimit.domain.PersonBudget;

/** Obergrenze, Verdrängung und atomare Änderung des Speichers der Durchsatzbremse (Issue #999). */
class InMemoryPersonBudgetStoreTest {

  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

  private final MutableClock clock = new MutableClock(NOW);

  private InMemoryPersonBudgetStore store(int maxTrackedPersons) {
    return new InMemoryPersonBudgetStore(
        new ThroughputProperties(true, 60, 10, maxTrackedPersons), clock);
  }

  private static PersonBudget.Limits limits() {
    return PersonBudget.Limits.of(60, 10);
  }

  @Test
  void update_passesNullForAnUnknownPerson_andStoresTheResult() {
    InMemoryPersonBudgetStore store = store(10);

    PersonBudget stored =
        store.update(
            7L,
            current -> {
              assertThat(current).isNull();
              return PersonBudget.fresh(NOW, limits());
            });

    assertThat(stored).isEqualTo(PersonBudget.fresh(NOW, limits()));
    assertThat(store.update(7L, current -> current)).isEqualTo(stored);
  }

  @Test
  void update_returningNull_dropsTheEntry() {
    InMemoryPersonBudgetStore store = store(10);
    store.update(7L, current -> PersonBudget.fresh(NOW, limits()));

    assertThat(store.update(7L, current -> null)).isNull();
    assertThat(store.tracked()).isZero();
  }

  @Test
  void beyondTheLimit_theTableDoesNotGrow() {
    InMemoryPersonBudgetStore store = store(10);

    IntStream.range(0, 100)
        .forEach(i -> store.update(i, current -> PersonBudget.fresh(NOW, limits())));

    assertThat(store.tracked()).isEqualTo(10);
  }

  @Test
  void onFullTable_idleEntriesGoFirst_andBusyOnesStay() {
    // Given: drei Plätze — zwei ruhende Personen, eine mit laufendem Aufruf.
    InMemoryPersonBudgetStore store = store(3);
    store.update(1L, current -> PersonBudget.fresh(NOW, limits()));
    store.update(2L, current -> PersonBudget.fresh(NOW, limits()));
    store.update(3L, current -> PersonBudget.fresh(NOW, limits()).admit(NOW, limits()));

    // When: eine vierte Person trifft auf die volle Tabelle.
    store.update(4L, current -> PersonBudget.fresh(NOW, limits()));

    // Then: beide ruhenden sind weg — unabhängig von der Iterationsreihenfolge; bei bloß
    // beliebiger Verdrängung stünden noch drei Einträge da. Der laufende Aufruf bleibt gezählt.
    assertThat(store.tracked()).isEqualTo(2);
    assertThat(store.update(3L, current -> current))
        .extracting(PersonBudget::inFlight)
        .isEqualTo(1);
  }

  @Test
  void onFullTable_withEveryoneBusy_exactlyOneEntryIsEvicted() {
    // Given: zwei Plätze, beide Personen mit laufendem Aufruf — nichts ist ruhend.
    InMemoryPersonBudgetStore store = store(2);
    store.update(1L, current -> PersonBudget.fresh(NOW, limits()).admit(NOW, limits()));
    store.update(2L, current -> PersonBudget.fresh(NOW, limits()).admit(NOW, limits()));

    // When
    store.update(3L, current -> PersonBudget.fresh(NOW, limits()));

    // Then: Die Obergrenze hält; verdrängt wurde genau einer, nicht beide.
    assertThat(store.tracked()).isEqualTo(2);
    assertThat(store.update(3L, current -> current)).isNotNull();
  }

  @Test
  void belowTheLimit_idleEntriesAreLeftAlone() {
    // Given: Platz ist genug; ein Scan bei jedem Befehl wäre der Aufwand, den der Speicher spart.
    InMemoryPersonBudgetStore store = store(10);
    store.update(1L, current -> PersonBudget.fresh(NOW, limits()));

    // When
    store.update(2L, current -> PersonBudget.fresh(NOW, limits()));

    // Then
    assertThat(store.tracked()).isEqualTo(2);
  }

  @Test
  void onFullTable_theKnownPersonEvictsNobody() {
    InMemoryPersonBudgetStore store = store(2);
    store.update(1L, current -> PersonBudget.fresh(NOW, limits()));
    store.update(2L, current -> PersonBudget.fresh(NOW, limits()));

    store.update(1L, current -> current);

    assertThat(store.tracked()).isEqualTo(2);
  }

  @Test
  void onFullTable_theIdleCheckUsesTheClock() {
    // Given: Die Person hat vor einer Sekunde einen Aufruf beendet — ihr Eimer ist erst nach
    // dieser Sekunde wieder voll. Ohne die Uhr des Speichers bliebe sie dauerhaft „beschäftigt".
    InMemoryPersonBudgetStore store = store(1);
    store.update(1L, current -> PersonBudget.fresh(NOW, limits()).admit(NOW, limits()).release());
    clock.advance(Duration.ofSeconds(1));

    // When
    store.update(2L, current -> PersonBudget.fresh(clock.instant(), limits()));

    // Then: Person 1 war ruhend und ist verdrängt, Person 2 steht drin.
    assertThat(store.tracked()).isEqualTo(1);
    assertThat(store.update(1L, current -> current)).isNull();
  }

  @Test
  void concurrentUpdates_ofOnePerson_loseNothing() {
    // Given: der Kern — gleichzeitige Befehle einer Person dürfen einander nicht überschreiben.
    InMemoryPersonBudgetStore store = store(10);
    store.update(1L, current -> PersonBudget.fresh(NOW, limits()));
    List<Integer> calls = IntStream.range(0, 1_000).boxed().toList();

    // When: tausend Zählschritte, parallel.
    calls.parallelStream().forEach(i -> store.update(1L, InMemoryPersonBudgetStoreTest::oneMore));

    // Then
    assertThat(store.update(1L, current -> current))
        .extracting(PersonBudget::inFlight)
        .isEqualTo(1_000);
  }

  private static PersonBudget oneMore(PersonBudget current) {
    return new PersonBudget(
        current.credit(), current.refilledAt(), current.inFlight() + 1, current.warnedAt());
  }
}
