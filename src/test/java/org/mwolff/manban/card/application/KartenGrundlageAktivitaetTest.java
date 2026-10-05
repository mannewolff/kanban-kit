package org.mwolff.manban.card.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.context.ApplicationEventPublisher;

/** Aktivitätseinträge mit Status nach der Bewegung (Issue #1426). */
class KartenGrundlageAktivitaetTest {

  private static final Instant JETZT = Instant.parse("2026-10-05T09:00:00Z");

  private final CardActivityRepository activity = mock(CardActivityRepository.class);
  private final ActorContext actor = mock(ActorContext.class);
  private final KartenGrundlage grundlage =
      new KartenGrundlage(
          mock(CardRepository.class),
          mock(KartenAbhaengigkeiten.class),
          mock(BoardService.class),
          mock(PermissionChecker.class),
          mock(CardColumnTransitionRepository.class),
          mock(KartenZuordnung.class),
          activity,
          actor,
          mock(ApplicationEventPublisher.class),
          Clock.fixed(JETZT, ZoneOffset.UTC));

  @Test
  void aktivitaet_reichtStatusNachDerBewegungUndStempelDurch() {
    // Given
    ActorStamp stamp =
        new ActorStamp(
            CardActivityOrigin.TOKEN,
            "Nachtlauf",
            "claude-opus-5-5",
            Instant.parse("2026-10-05T08:58:22.123Z"));
    when(actor.current()).thenReturn(stamp);

    // When
    grundlage.aktivitaet(7L, 3L, CardActivityType.MOVED, "Verschoben", JETZT, CardStatus.IN_REVIEW);

    // Then
    verify(activity)
        .add(7L, 3L, CardActivityType.MOVED, "Verschoben", JETZT, stamp, CardStatus.IN_REVIEW);
  }
}
