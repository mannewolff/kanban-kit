package org.mwolff.manban.nightrun.application;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.RetainedByKind;

/**
 * Die Aufbewahrungsgrenze (Plan #1067 E8, Issue #1071; herausgezogen in Issue #2002, Plan #1990
 * E3): Eine Gattung hat nur dann eine Grenze, wenn ihr Ringpuffer <b>gefüllt</b> ist ({@code count
 * >= properties.maxRunsFor(kind)}) — erst dann ist etwas verdrängt worden. Die Grenze ist ihr
 * ältester aufbewahrter Eintrag.
 *
 * <p>Die Verbrauchs-Auswertung fragt nach der Grenze über alle Gattungen, die Kostenvoraussage nach
 * der Grenze der Nachtläufe allein; beide stützen sich auf dieselbe Regel.
 */
public final class Aufbewahrungsgrenze {

  private final NightRunProperties properties;

  public Aufbewahrungsgrenze(NightRunProperties properties) {
    this.properties = properties;
  }

  /** Die Grenze dieser Gattung; leer, solange ihr Ringpuffer nicht gefüllt ist. */
  public Optional<Instant> jeGattung(RetainedByKind aufbewahrt) {
    if (aufbewahrt.count() < properties.maxRunsFor(aufbewahrt.kind())) {
      return Optional.empty();
    }
    return Optional.ofNullable(aufbewahrt.oldestStart());
  }

  /**
   * Der späteste älteste Eintrag unter den Gattungen mit gefülltem Ringpuffer. Hat keine Gattung
   * ihren Ringpuffer gefüllt, gibt es keine Grenze, unabhängig davon, wie alt das Projekt ist.
   *
   * <p>Hält eine nicht volle Gattung ältere Einträge als die volle, bleibt die Grenze am ältesten
   * Eintrag der vollen Gattung — die nicht volle hat davon nichts verloren (E9, Plan-Review H5).
   */
  public Optional<Instant> gesamt(Collection<RetainedByKind> aufbewahrt) {
    return aufbewahrt.stream()
        .map(this::jeGattung)
        .flatMap(Optional::stream)
        .max(Instant::compareTo);
  }
}
