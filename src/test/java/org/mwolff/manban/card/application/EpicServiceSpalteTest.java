package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Vorhaben-Fortschritt mit einem Mitglied in einer Spalte, die das Board nicht mehr führt (Issue
 * #1220). Die Testmethode stammt unverändert aus {@link CardServiceCreateBatchTest} (Issue #1393,
 * Plan #1387 E6) und behält deren Aufbau: In {@link EpicServiceTest} kollidierte die Konstante
 * {@code COLUMN} mit dem dortigen Helfer {@code column(...)} (PMD
 * AvoidFieldNameMatchingMethodName).
 */
class EpicServiceSpalteTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;
  private static final long COLUMN = 20L;

  private CardRepository cards;
  private BoardService boardService;
  private EpicService service;

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    boardService = mock(BoardService.class);
    ActorContext actor = mock(ActorContext.class);
    when(actor.current()).thenReturn(ActorContext.ActorStamp.unknown());
    service =
        CardServiceAufbau.epicAusPorts(
            cards,
            mock(CardDependencyRepository.class),
            boardService,
            mock(PermissionChecker.class),
            mock(CardColumnTransitionRepository.class),
            new KartenZuordnung(
                mock(CardAssigneeRepository.class),
                mock(LabelRepository.class),
                mock(CardLabelRepository.class),
                mock(PermissionChecker.class)),
            mock(CardActivityRepository.class),
            actor,
            mock(ApplicationEventPublisher.class),
            Clock.fixed(FIXED, ZoneOffset.UTC));
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
  }

  private static ColumnView spalte(String name) {
    return new ColumnView(COLUMN, name, 0, null);
  }

  /**
   * Die Done-Erkennung dient auch der Fortschrittszählung der Vorhaben ({@code listEpics}). Dort
   * kommt der Spaltenname aus einer Map und fehlt, wenn die Spalte nicht mehr im Board steht — eine
   * solche Karte zählt nicht als erledigt (Issue #1220).
   */
  @Test
  void listEpics_mitgliedInUnbekannterSpalte_zaehltNichtAlsErledigt() {
    // Given: Spalte 21 taucht in listColumns nicht auf, ihr Name ist also unbekannt.
    when(boardService.listColumns(BOARD)).thenReturn(List.of(spalte("Done")));
    when(cards.findByBoardId(BOARD))
        .thenReturn(List.of(vorhaben(5L), mitglied(6L, 21L, 2), mitglied(7L, COLUMN, 3)));

    // When
    List<EpicService.EpicView> result = service.listEpics(1L, BOARD);

    // Then: nur die Karte in der bekannten Done-Spalte zählt.
    assertThat(result)
        .singleElement()
        .extracting(EpicService.EpicView::done, EpicService.EpicView::total)
        .containsExactly(1, 2);
  }

  private static Card vorhaben(long id) {
    return karte(id, COLUMN, 1, CardType.EPIC, null);
  }

  private static Card mitglied(long id, long columnId, int number) {
    return karte(id, columnId, number, CardType.CARD, 5L);
  }

  private static Card karte(
      long id, long columnId, int number, CardType type, @Nullable Long parentId) {
    return new Card(
        id, BOARD, columnId, number, "Titel", null, 0, false, null, 1L, FIXED, FIXED, type,
        parentId, null, null, PROJECT, null, null, null, null);
  }
}
