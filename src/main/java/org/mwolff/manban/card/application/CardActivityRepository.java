package org.mwolff.manban.card.application;

import java.time.Instant;
import java.util.List;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardActivityType;

/** Ausgehender Port für den Aktivitätsverlauf einer Karte. */
public interface CardActivityRepository {

  /** Hält einen Aktivitätseintrag fest. */
  void add(long cardId, long actorUserId, CardActivityType type, String detail, Instant createdAt);

  /** Aktivitäten der Karte, chronologisch nach Zeitpunkt. */
  List<CardActivity> findByCardId(long cardId);
}
