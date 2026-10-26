package org.mwolff.manban.card.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Aktivitätseinträge mit Status nach der Bewegung (Issue #1426) und die handelnde Person beim
 * Anlegen mit Zuständigen (Issue #1916).
 */
class KartenGrundlageAktivitaetTest {

  private static final Instant JETZT = Instant.parse("2026-10-05T09:00:00Z");

  private final CardActivityRepository activity = mock(CardActivityRepository.class);
  private final ActorContext actor = mock(ActorContext.class);
  private final CardRepository cards = mock(CardRepository.class);
  private final BoardService boardService = mock(BoardService.class);
  private final KartenZuordnung zuordnung = mock(KartenZuordnung.class);
  private final KartenGrundlage grundlage =
      new KartenGrundlage(
          cards,
          mock(KartenAbhaengigkeiten.class),
          boardService,
          mock(PermissionChecker.class),
          mock(CardColumnTransitionRepository.class),
          zuordnung,
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

  @Test
  void anlegen_reichtDieHandelndePersonAnDieZuordnungDurch() {
    // Issue #1916: Auch beim Anlegen mit Zuständigen erfährt die Zuordnung, wer handelt.
    ColumnView spalte = new ColumnView(20L, "Backlog", 0, null);
    when(boardService.requireProjectId(10L)).thenReturn(1L);
    when(boardService.requireColumn(20L, 10L)).thenReturn(spalte);
    when(boardService.listColumns(10L)).thenReturn(List.of(spalte));
    when(cards.allocateCardNumber(1L)).thenReturn(4);
    when(cards.save(any(Card.class))).thenAnswer(inv -> mitId(inv.getArgument(0)));

    grundlage.anlegen(
        3L, 10L, 20L, "Titel", null, null, null, null, List.of(7L), null, null, null, null);

    verify(zuordnung).ersetzeZustaendige(55L, 1L, List.of(7L), 3L);
  }

  private static Card mitId(Card c) {
    return new Card(
        55L,
        c.boardId(),
        c.columnId(),
        c.number(),
        c.title(),
        c.description(),
        c.positionInColumn(),
        c.archived(),
        c.movedToDoneAt(),
        c.createdBy(),
        c.createdAt(),
        c.updatedAt(),
        c.type(),
        c.parentId(),
        c.shortcode(),
        c.dueDate(),
        c.projectId(),
        c.externalKey(),
        c.derivedFromCardId(),
        c.requirementCardId(),
        c.status());
  }
}
