package org.mwolff.manban.card.application;

import java.time.Instant;
import java.util.List;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardActivityType;

/** Ausgehender Port für den Aktivitätsverlauf einer Karte. */
public interface CardActivityRepository {

  /** Hält einen Aktivitätseintrag samt Herkunfts-Stempel (Issue #517) fest. */
  void add(
      long cardId,
      long actorUserId,
      CardActivityType type,
      String detail,
      Instant createdAt,
      ActorStamp stamp);

  /** Aktivitäten der Karte, chronologisch nach Zeitpunkt. */
  List<CardActivity> findByCardId(long cardId);
}
