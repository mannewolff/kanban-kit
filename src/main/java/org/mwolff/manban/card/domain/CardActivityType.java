package org.mwolff.manban.card.domain;

/** Art eines Aktivitätseintrags einer Karte. */
public enum CardActivityType {
  CREATED,
  MOVED,
  UPDATED,
  ASSIGNED,
  ARCHIVED,
  RESTORED,

  // Historische Werte aus der Zeit des Ideen-Pools (Issue #1204, E17): Sie werden nur noch
  // GELESEN — kein Schreibpfad setzt sie mehr. Sie bleiben stehen, weil `card_activity.type` als
  // Text gespeichert und mit `CardActivityType.valueOf(...)` ohne Rückfall gelesen wird: Ohne die
  // beiden Konstanten antwortete `GET /api/cards/{id}/activity` für jede Karte, die je durch den
  // Pool gelaufen ist, mit 500. Bestandsdaten bleiben unverändert (AK 13), die Migration V44
  // (#1205) fasst `card_activity` bewusst nicht an.
  IDEA_STORED,
  PROMOTED
}
