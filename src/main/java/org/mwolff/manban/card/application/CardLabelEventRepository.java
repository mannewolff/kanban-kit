package org.mwolff.manban.card.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.domain.CardLabelEvent;
import org.mwolff.manban.card.domain.LabelEreignisZeile;

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

  /**
   * Die Ereignisse mit einem der Label-Namen, deren {@code created_at} nicht vor {@code seit} liegt
   * (Issue #2075), mit und ohne Laufkennung; chronologisch, bei Gleichstand nach ID. Ohne Namen
   * eine leere Liste.
   */
  List<LabelEreignisZeile> findNachLabelNamenSeit(Collection<String> labelNamen, Instant seit);

  /** Seit wann Label-Ereignisse erfasst werden — der Zeitpunkt der Migration V55 (Plan E7). */
  Instant erfassungSeit();
}
