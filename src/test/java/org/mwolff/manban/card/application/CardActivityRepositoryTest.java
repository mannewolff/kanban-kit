package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardStatus;

/**
 * Die Default-Methode {@link CardActivityRepository#add(long, long, CardActivityType, String,
 * Instant, ActorStamp)} reicht an die Fassung mit {@code statusAfter} weiter (Issue #1461).
 *
 * <p>Mit einer eigenen Implementierung des Ports statt eines Mocks: Die Dienst-Tests mocken das
 * ganze Interface und damit auch die Default-Methode — ohne diesen Test überlebte der Mutant, der
 * den Weiterreich-Aufruf löscht.
 */
class CardActivityRepositoryTest {

  private static final Instant JETZT = Instant.parse("2026-10-05T12:00:00Z");

  /** Ein festgehaltener Aufruf der vollen {@code add}-Fassung. */
  private record Aufruf(
      long cardId,
      long actorUserId,
      CardActivityType type,
      String detail,
      Instant createdAt,
      ActorStamp stamp,
      @Nullable CardStatus statusAfter) {}

  /** Hält nur die volle {@code add}-Fassung fest; alles andere gehört nicht zu diesem Test. */
  private static final class Mitschrift implements CardActivityRepository {

    private final List<Aufruf> aufrufe = new ArrayList<>();

    @Override
    public void add(
        long cardId,
        long actorUserId,
        CardActivityType type,
        String detail,
        Instant createdAt,
        ActorStamp stamp,
        @Nullable CardStatus statusAfter) {
      aufrufe.add(new Aufruf(cardId, actorUserId, type, detail, createdAt, stamp, statusAfter));
    }

    @Override
    public List<CardActivity> findByCardId(long cardId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<CardActivity> findTokenActivitiesInWindow(
        long projectId, String tokenName, Instant von, Instant bis) {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  void addOhneStatusReichtAlleWerteMitStatusNullWeiter() {
    Mitschrift repository = new Mitschrift();
    ActorStamp stamp = new ActorStamp(CardActivityOrigin.TOKEN, "Nacht-Token", "opus", JETZT);

    repository.add(7L, 3L, CardActivityType.MOVED, "Ready → In progress", JETZT, stamp);

    assertThat(repository.aufrufe)
        .containsExactly(
            new Aufruf(7L, 3L, CardActivityType.MOVED, "Ready → In progress", JETZT, stamp, null));
  }
}
