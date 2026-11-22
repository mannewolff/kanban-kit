package org.mwolff.manban.accesstoken.application;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * Lesender Port: Wann hat das Rechenzentrum die Karten eines Boards zuletzt gelesen (Issue #2216,
 * Plan #2206 E12)? Gemessen wird am jüngsten {@code last_used_at} der Zugänge des Rechenzentrums —
 * nicht widerrufen, an das Board gebunden, Art {@code STANDARD}, Inhaber echter OWNER oder ADMIN
 * des gebundenen Projekts (dieselbe Bestimmung wie bei Beiträgen der KI, E6). Zugänge der App und
 * gewöhnliche Zugänge anderer Mitglieder zählen nicht: Ein Projekt mit CLI- oder Kit-Zugang eines
 * Mitglieds zeigte sonst nach kurzer Ruhe „gerade nicht erreichbar“.
 *
 * <p>Prüft keine Rechte; der Aufrufer prüft vorher, dass die Boards den Aufrufer etwas angehen.
 */
@FunctionalInterface
public interface ZugangsNutzung {

  /**
   * Je Board der jüngste Zeitpunkt, zu dem ein Zugang des Rechenzentrums benutzt wurde. Boards ohne
   * einen solchen Zeitpunkt fehlen in der Map.
   */
  Map<Long, Instant> juengsteNutzungDesRechenzentrums(Collection<Long> boardIds);
}
