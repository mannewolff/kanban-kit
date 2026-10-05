package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit-Tests des Status an den Schreibpfaden des Kanban-kompatiblen Einlieferns (Plan #1294, Issue
 * #1299): Anlegen direkt in einer Spalte und Umbenennen führen den Status eines Arbeitspakets mit.
 * Die Testmethoden stammen unverändert aus {@link CardServiceStatusTest} (Issue #1392, Plan #1387
 * E6).
 */
class CardIngestServiceStatusTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;

  private CardRepository cards;
  private BoardService boardService;
  private CardIngestService service;

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    boardService = mock(BoardService.class);
    PermissionChecker permissions = mock(PermissionChecker.class);
    ActorContext actor = mock(ActorContext.class);
    when(actor.current()).thenReturn(ActorContext.ActorStamp.unknown());
    service =
        CardServiceAufbau.ingestAusPorts(
            cards,
            mock(CardDependencyRepository.class),
            boardService,
            permissions,
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
    when(cards.save(any(Card.class))).thenAnswer(inv -> mitId(inv.getArgument(0)));
  }

  /** Die gespeicherte Karte mit Id — alle übrigen Felder, auch der Status, bleiben erhalten. */
  private static Card mitId(Card c) {
    return new Card(
        c.id() == null ? 1L : c.id(),
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

  private static ColumnView column(long id, String name, int position) {
    return new ColumnView(id, name, position, null);
  }

  /** Karte mit Titel, Status und Done-Zeitstempel — für die Statusregeln der Schreibpfade. */
  private static Card paket(
      long id, long columnId, String title, @Nullable CardStatus status, @Nullable Instant done) {
    return new Card(
        id,
        BOARD,
        columnId,
        1,
        title,
        null,
        0,
        false,
        done,
        1L,
        FIXED,
        FIXED,
        CardType.CARD,
        null,
        null,
        null,
        PROJECT,
        null,
        null,
        null,
        status);
  }

  private Card gespeichert() {
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    verify(cards).save(captor.capture());
    return captor.getValue();
  }

  private void stubAnlegen(String spaltenname) {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, spaltenname, 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
  }

  @Test
  void createDirect_arbeitspaket_uebernimmtDenStatusDerSpalte() {
    stubAnlegen("Ready");

    service.createDirect(
        1L, BOARD, 20L, new CardIngestService.DirectCard("Finding", null, "sonar:x", null, null));

    assertThat(gespeichert().status()).isEqualTo(CardStatus.READY);
  }

  @Test
  void updateContent_arbeitspaketWirdDokument_raeumtDenStatus() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(paket(1L, 20L, "Paket", CardStatus.READY, null)));
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Ready", 1));

    service.updateContent(1L, 1L, "[Fachlich] Paket", null);

    assertThat(gespeichert().status()).isNull();
  }

  @Test
  void updateContent_dokumentWirdArbeitspaket_bekommtDenStatusDerProzessspalte() {
    when(cards.findById(1L)).thenReturn(Optional.of(paket(1L, 20L, "[Idee] Einfall", null, null)));
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "In Progress", 2));

    service.updateContent(1L, 1L, "Einfall", null);

    assertThat(gespeichert().status()).isEqualTo(CardStatus.IN_PROGRESS);
  }
}
