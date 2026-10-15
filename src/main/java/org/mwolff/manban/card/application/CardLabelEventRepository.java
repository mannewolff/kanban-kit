package org.mwolff.manban.card.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.domain.CardLabelEvent;

/**
 * Ausgehender Port für die Label-Ereignisse einer Karte (Issue #1754, Plan #1752 E2): Jedes
 * tatsächliche Hinzufügen eines Labels wird mit dem Herkunfts-Stempel festgehalten, Abnehmen nicht.
 */
public interface CardLabelEventRepository {

  /** Hält fest, dass der Karte das Label {@code labelName} zum {@code zeitpunkt} hinzukam. */
  void add(long cardId, String labelName, Instant zeitpunkt, ActorStamp stamp);

  /**
   * Die Ereignisse der Karten, die ein Lauf gesetzt hat: Laufkennung gesetzt und nicht vor {@code
   * von}; chronologisch, bei Gleichstand nach ID. Ohne Karten-IDs eine leere Liste.
   */
  List<CardLabelEvent> findLaufEreignisse(Collection<Long> cardIds, Instant von);

  /** Seit wann Label-Ereignisse erfasst werden — der Zeitpunkt der Migration V55 (Plan E7). */
  Instant erfassungSeit();
}
