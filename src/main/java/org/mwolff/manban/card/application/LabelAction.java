package org.mwolff.manban.card.application;

/**
 * Richtung der Label-Massenaktion {@link CardService#bulkLabels}: Ein Label wird allen gewählten
 * Karten hinzugefügt oder allen abgenommen.
 *
 * <p>Bewusst kein Ersetzen der Label-Menge: Das löschte fremde Labels der Karten still mit (Issue
 * #994).
 */
public enum LabelAction {
  /**
   * Das Label zur vorhandenen Menge hinzufügen; Karten, die es schon tragen, bleiben unverändert.
   */
  ADD,
  /** Das Label aus der vorhandenen Menge entfernen; Karten ohne das Label bleiben unverändert. */
  REMOVE
}
