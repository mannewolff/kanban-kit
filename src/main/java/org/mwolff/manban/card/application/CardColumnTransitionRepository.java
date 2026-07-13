package org.mwolff.manban.card.application;

import java.time.Instant;
import java.util.List;
import org.mwolff.manban.card.domain.CardColumnTransition;

/**
 * Ausgehender Port für die Historie der Spaltenaufenthalte einer Karte. Ein Spaltenwechsel besteht
 * aus {@link #closeOpen} (verlassene Spalte) gefolgt von {@link #open} (Zielspalte).
 */
public interface CardColumnTransitionRepository {

  /** Eröffnet eine offene Zeile für den Eintritt der Karte in die Spalte. */
  void open(long cardId, long columnId, String columnName, Instant enteredAt);

  /**
   * Schließt die offene Zeile der Karte ({@code left_at IS NULL}): setzt {@code left_at} und {@code
   * duration_seconds} (Sekunden zwischen Eintritt und {@code leftAt}). No-op, wenn keine offene
   * Zeile existiert.
   */
  void closeOpen(long cardId, Instant leftAt);

  /** Alle Aufenthalte der Karte, chronologisch nach Eintritt. */
  List<CardColumnTransition> findByCardId(long cardId);
}
