package org.mwolff.manban.card.domain;

/**
 * Art eines Karten-Datensatzes: normale Karte oder Vorhaben (Gruppierung mehrerer Karten).
 *
 * <p><b>Der gespeicherte Wert heißt weiterhin {@code EPIC}, obwohl die Oberfläche „Vorhaben" sagt —
 * und das ist Absicht, kein Rest einer unvollständigen Umbenennung.</b> Der Konstantenname
 * <i>ist</i> der gespeicherte Wert: {@code CardRepositoryAdapter} liest ihn über {@code
 * CardType.valueOf} zurück. Darauf stehen die Check-Constraint {@code chk_card_type} (Migration
 * {@code V2}) und die generierte Spalte {@code active_position}, zuletzt definiert in Migration
 * {@code V16}. Diese Konstante umzubenennen wäre deshalb keine Refactoring-, sondern eine
 * Datenmigrationsfrage.
 */
public enum CardType {
  CARD,
  EPIC
}
