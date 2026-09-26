package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectService;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit-Tests des Karten-Stapels {@code createCardsBatch} (Issue #1200) und des Done-Zeitstempels
 * beim Anlegen ({@code doneStempel}, #1200 E4).
 *
 * <p>Eigene Klasse und nicht ein weiterer Block in {@code CardServiceTest} — wie schon bei {@link
 * CardServiceListByBoardTest}: Jene Klasse steht an ihren PMD-Grenzen (Größe und Import-Zahl).
 *
 * <p>Die Tests halten die <b>Rückgabe</b> des Stapels fest (Reihenfolge, Vollständigkeit, kein
 * {@code null}) und beide Seiten der Done-Erkennung — Spaltenname mit und ohne „done", in
 * beliebiger Schreibweise, sowie eine Karte in einer Spalte ohne bekannten Namen (Issue #1220).
 */
class CardServiceCreateBatchTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;
  private static final long COLUMN = 20L;

  private CardRepository cards;
  private BoardService boardService;
  private CardService service;

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    boardService = mock(BoardService.class);
    ActorContext actor = mock(ActorContext.class);
    when(actor.current()).thenReturn(ActorContext.ActorStamp.unknown());
    service =
        new CardService(
            cards,
            mock(CardDependencyRepository.class),
            boardService,
            mock(PermissionChecker.class),
            mock(ProjectService.class),
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
    // Die gespeicherte Karte bekommt ihre Nummer als Id: So ist in der Rückgabe des Stapels
    // nachvollziehbar, welche Karte an welcher Stelle steht.
    when(cards.save(any(Card.class))).thenAnswer(inv -> mitId(inv.getArgument(0)));
  }

  private static Card mitId(Card c) {
    return new Card(
        Long.valueOf(c.number()),
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
        null,
        null);
  }

  private static ColumnView spalte(String name) {
    return new ColumnView(COLUMN, name, 0, null);
  }

  private void spalteMitNamen(String name) {
    when(boardService.requireColumn(COLUMN, BOARD)).thenReturn(spalte(name));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1, 2, 3);
    when(cards.allocateActivePosition(COLUMN)).thenReturn(0, 1, 2);
  }

  @Test
  void createCardsBatch_liefertAlleKartenInEingabereihenfolge() {
    // Given
    spalteMitNamen("Backlog");

    // When
    List<CardService.CardView> result =
        service.createCardsBatch(
            1L,
            BOARD,
            COLUMN,
            List.of(
                new CardService.NewCard("Erste", null),
                new CardService.NewCard("Zweite", "Text"),
                new CardService.NewCard("Dritte", null)));

    // Then: genau drei Karten, keine davon null, in der Reihenfolge der Eingabe.
    assertThat(result).doesNotContainNull().hasSize(3);
    assertThat(result)
        .extracting(
            CardService.CardView::title,
            CardService.CardView::number,
            CardService.CardView::positionInColumn)
        .containsExactly(tuple("Erste", 1, 0), tuple("Zweite", 2, 1), tuple("Dritte", 3, 2));
  }

  @Test
  void createCardsBatch_einElement_liefertGenauDieseKarte() {
    // Given
    spalteMitNamen("Backlog");

    // When
    List<CardService.CardView> result =
        service.createCardsBatch(
            1L, BOARD, COLUMN, List.of(new CardService.NewCard("Einzeln", null)));

    // Then
    assertThat(result)
        .singleElement()
        .extracting(CardService.CardView::title, CardService.CardView::id)
        .containsExactly("Einzeln", 1L);
  }

  @Test
  void create_inDoneSpalte_setztMovedToDoneAt() {
    // Given: Grossschreibung — die Erkennung normalisiert den Namen.
    spalteMitNamen("DONE");

    // When
    CardService.CardView view = service.create(1L, BOARD, COLUMN, "Titel", null, null, null);

    // Then
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    verifySave(captor);
    assertThat(captor.getValue().movedToDoneAt()).isEqualTo(FIXED);
    assertThat(view.movedToDoneAt()).isEqualTo(FIXED);
  }

  @Test
  void create_ausserhalbEinerDoneSpalte_laesstMovedToDoneAtLeer() {
    // Given
    spalteMitNamen("In Progress");

    // When
    CardService.CardView view = service.create(1L, BOARD, COLUMN, "Titel", null, null, null);

    // Then
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    verifySave(captor);
    assertThat(captor.getValue().movedToDoneAt()).isNull();
    assertThat(view.movedToDoneAt()).isNull();
  }

  @Test
  void createCardsBatch_inDoneSpalte_stempeltJedeKarte() {
    // Given
    spalteMitNamen("Fertig / Done");

    // When
    List<CardService.CardView> result =
        service.createCardsBatch(
            1L,
            BOARD,
            COLUMN,
            List.of(new CardService.NewCard("A", null), new CardService.NewCard("B", null)));

    // Then
    assertThat(result)
        .extracting(CardService.CardView::movedToDoneAt)
        .containsExactly(FIXED, FIXED);
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
    List<CardService.EpicView> result = service.listEpics(1L, BOARD);

    // Then: nur die Karte in der bekannten Done-Spalte zählt.
    assertThat(result)
        .singleElement()
        .extracting(CardService.EpicView::done, CardService.EpicView::total)
        .containsExactly(1, 2);
  }

  private void verifySave(ArgumentCaptor<Card> captor) {
    verify(cards).save(captor.capture());
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
        parentId, null, null, PROJECT, null, null, null);
  }
}
