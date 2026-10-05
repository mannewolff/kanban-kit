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

  /**
   * Die Aktivitäten eines Nachtlaufs im Projekt (Issue #1373, Plan #1372 E2): Herkunft {@code
   * TOKEN} mit genau diesem Token-Namen, gesetztes {@code agent} und Zeitpunkt in {@code [von,
   * bis]}, beide Grenzen eingeschlossen; chronologisch, bei Gleichstand nach ID.
   *
   * <p><b>Warum {@code agent} gesetzt sein muss:</b> Interaktive Sitzungen laufen über dasselbe
   * Token, schicken aber keine Modell-Angabe — nur der Nachtbetrieb setzt sie. Ohne die Bedingung
   * zählten die Handgriffe einer Tagessitzung zum Lauf.
   */
  List<CardActivity> findTokenActivitiesInWindow(
      long projectId, String tokenName, Instant von, Instant bis);
}
