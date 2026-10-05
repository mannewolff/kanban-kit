package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Verhaltenstests von Archiv und Papierkorb (Mockito an den Ports). Die Testmethoden stammen
 * unverändert aus {@code CardServiceTest} (Issue #1394, Plan #1387 E6); {@link KartenGrundlage},
 * {@link KartenSicht} und {@link KartenAbhaengigkeiten} entstehen echt aus denselben Port-Mocks.
 */
// PMD.CouplingBetweenObjects: wandert mit den Testmethoden aus CardServiceTest (Issue #1394) — der
// Prüfling entsteht aus den Ports, die KartenGrundlage und KartenSicht echt brauchen (Plan #1387,
// E6), wie in EpicServiceTest. Ein Mock der Bausteine senkte die Kopplung, ließe Abdeckung und
// Mutationsprüfung der Helfer aber ins Leere laufen.
@SuppressWarnings("PMD.CouplingBetweenObjects")
class CardArchiveServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;

  private CardRepository cards;
  private CardDependencyRepository dependencies;
  private BoardService boardService;
  private PermissionChecker permissions;
  private CardColumnTransitionRepository transitions;
  private CardAssigneeRepository assignees;
  private LabelRepository labels;
  private CardLabelRepository cardLabels;
  private CardActivityRepository activity;
  private ActorContext actor;
  private ApplicationEventPublisher events;
  private CardArchiveService service;

  private static Card card(
      long id,
      long columnId,
      int number,
      boolean archived,
      Instant done,
      CardType type,
      Long parentId,
      String shortcode) {
    return new Card(
        id, BOARD, columnId, number, "Titel", null, 0, archived, done, 1L, FIXED, FIXED, type,
        parentId, shortcode, null, PROJECT, null, null, null, null);
  }

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    dependencies = mock(CardDependencyRepository.class);
    boardService = mock(BoardService.class);
    permissions = mock(PermissionChecker.class);
    transitions = mock(CardColumnTransitionRepository.class);
    assignees = mock(CardAssigneeRepository.class);
    labels = mock(LabelRepository.class);
    cardLabels = mock(CardLabelRepository.class);
    activity = mock(CardActivityRepository.class);
    actor = mock(ActorContext.class);
    when(actor.current()).thenReturn(ActorContext.ActorStamp.unknown());
    events = mock(ApplicationEventPublisher.class);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    service =
        CardServiceAufbau.archiveAusPorts(
            cards,
            dependencies,
            boardService,
            permissions,
            transitions,
            // Echte KartenZuordnung aus denselben Port-Mocks (Issue #1051): Die Prüfungen für
            // Zuständige und Labels sind nur aus dem Service herausgezogen, nicht ersetzt — ein
            // Mock der Zuordnung ließe die Tests zu InvalidAssigneeException/InvalidLabelException
            // ins Leere laufen.
            new KartenZuordnung(assignees, labels, cardLabels, permissions),
            activity,
            actor,
            events,
            clock);
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(cards.save(any(Card.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
  }

  private static Card withId(Card c) {
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
        null,
        null,
        null);
  }

  private CardBoardActivityEvent onlyPublishedEvent() {
    ArgumentCaptor<CardBoardActivityEvent> captor =
        ArgumentCaptor.forClass(CardBoardActivityEvent.class);
    verify(events).publishEvent(captor.capture());
    return captor.getValue();
  }

  @Test
  void archive_marksCardArchived() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.archive(1L, 1L);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().archived()).isTrue();
  }

  @Test
  void archive_requiresEpicDeletePermission_forEpic() {
    // Given
    when(cards.findById(5L))
        .thenReturn(Optional.of(card(5L, 20L, 5, false, null, CardType.EPIC, null, "E")));

    // When
    service.archive(1L, 5L);

    // Then
    verify(permissions).require(1L, 1L, Permission.EPIC_DELETE);
  }

  @Test
  void archive_throwsCardNotFound_whenCardUnknown() {
    // Given
    when(cards.findById(1L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.archive(1L, 1L)).isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void restore_appendsAtNextPosition() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, true, null, CardType.CARD, null, null)));
    when(cards.allocateActivePosition(20L)).thenReturn(3);

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.restore(1L, 1L);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().positionInColumn()).isEqualTo(3);
  }

  @Test
  void delete_softDeletesCard() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    // When
    service.delete(1L, 1L);

    // Then — Löschen ist reversibel (Papierkorb), kein Hard-Delete.
    verify(cards).softDelete(1L, FIXED);
    verify(cards, never()).deleteById(anyLong());
    // Kein Epic -> keine Kinder-Entkopplung (findByBoardId bleibt ungenutzt).
    verify(cards, never()).findByBoardId(anyLong());
  }

  @Test
  void bulkDelete_softDeletesEveryCard() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(cards.findById(2L))
        .thenReturn(Optional.of(card(2L, 20L, 2, false, null, CardType.CARD, null, null)));

    // When
    service.bulkDelete(9L, List.of(1L, 2L));

    // Then
    verify(cards).softDelete(1L, FIXED);
    verify(cards).softDelete(2L, FIXED);
  }

  @Test
  void bulkDelete_propagatesAndDeletesNoneWhenOneCardUnknown() {
    // Given: erste ID unbekannt -> Fehler vor jeglichem Soft-Delete (Rollback im echten Betrieb)
    when(cards.findById(2L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.bulkDelete(9L, List.of(2L, 1L)))
        .isInstanceOf(CardNotFoundException.class);
    verify(cards, never()).softDelete(anyLong(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void delete_epicUnassignsChildrenBeforeSoftDelete() {
    when(cards.findById(5L))
        .thenReturn(Optional.of(card(5L, 20L, 5, false, null, CardType.EPIC, null, "E")));
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                card(1L, 20L, 1, false, null, CardType.CARD, 5L, null),
                card(2L, 20L, 2, false, null, CardType.CARD, null, null)));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.delete(9L, 5L);

    // Nur das Kind des Epics wird von seiner Zuordnung gelöst; danach das Epic soft-gelöscht.
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().id()).isEqualTo(1L);
    assertThat(captor.getValue().parentId()).isNull();
    verify(cards).softDelete(5L, FIXED);
  }

  @Test
  void delete_requiresTicketDeletePermission_forCard() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    // When
    service.delete(1L, 1L);

    // Then
    verify(permissions).require(1L, 1L, Permission.TICKET_DELETE);
  }

  @Test
  void archive_returnsArchivedView() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    // When
    CardView view = service.archive(1L, 1L);

    // Then
    assertThat(view.archived()).isTrue();
  }

  @Test
  void restore_returnsRestoredView() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, true, null, CardType.CARD, null, null)));

    // When
    CardView view = service.restore(1L, 1L);

    // Then
    assertThat(view.archived()).isFalse();
  }

  @Test
  void bulkArchive_archivesEveryCardAndReturnsViews() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(cards.findById(2L))
        .thenReturn(Optional.of(card(2L, 20L, 2, false, null, CardType.CARD, null, null)));

    // When
    List<CardView> result = service.bulkArchive(9L, List.of(1L, 2L));

    // Then
    assertThat(result).hasSize(2).allMatch(CardView::archived);
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    verify(cards, times(2)).save(captor.capture());
    assertThat(captor.getAllValues()).allMatch(Card::archived);
  }

  @Test
  void bulkArchive_propagatesAndStopsWhenOneCardUnknown() {
    // Given: erste ID unbekannt -> Fehler vor jeglicher Speicherung (Rollback im echten Betrieb)
    when(cards.findById(2L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.bulkArchive(9L, List.of(2L, 1L)))
        .isInstanceOf(CardNotFoundException.class);
    verify(cards, never()).save(org.mockito.ArgumentMatchers.any(Card.class));
  }

  @Test
  void restoreFromTrash_clearsDeletionAndAppends() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(cards.allocateActivePosition(20L)).thenReturn(5);

    CardView view = service.restoreFromTrash(9L, 1L);

    verify(cards).restoreFromTrash(1L, 5);
    verify(activity)
        .add(
            1L,
            9L,
            CardActivityType.RESTORED,
            "Aus Papierkorb wiederhergestellt",
            FIXED,
            ActorContext.ActorStamp.unknown());
    assertThat(view.id()).isEqualTo(1L);
  }

  @Test
  void purge_hardDeletes_forBoardManager() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.purge(9L, 1L);

    verify(permissions).require(9L, 1L, Permission.BOARD_DELETE);
    verify(dependencies).deleteByCardId(1L);
    verify(cards).deleteById(1L);
  }

  @Test
  void purge_throwsCardNotFound_whenUnknown() {
    when(cards.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.purge(9L, 1L)).isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void listTrash_returnsOnlyTrashedCards() {
    when(cards.findTrashByBoardId(BOARD))
        .thenReturn(
            List.of(
                card(1L, 20L, 1, false, null, CardType.CARD, null, null),
                card(2L, 20L, 2, false, null, CardType.EPIC, null, "E")));

    List<CardView> trash = service.listTrash(5L, BOARD);

    verify(permissions).requireMembership(5L, 1L);
    assertThat(trash).extracting(CardView::id).containsExactly(1L);
  }

  @Test
  void archive_recordsArchivedActivity() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.archive(9L, 1L);

    verify(activity)
        .add(
            1L,
            9L,
            CardActivityType.ARCHIVED,
            "Archiviert",
            FIXED,
            ActorContext.ActorStamp.unknown());
  }

  @Test
  void restore_recordsRestoredActivity() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, true, null, CardType.CARD, null, null)));

    service.restore(9L, 1L);

    verify(activity)
        .add(
            1L,
            9L,
            CardActivityType.RESTORED,
            "Wiederhergestellt",
            FIXED,
            ActorContext.ActorStamp.unknown());
  }

  @Test
  void archive_publishesArchivedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.archive(1L, 1L);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.ARCHIVED, 1L));
  }

  @Test
  void restore_publishesRestoredEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, true, null, CardType.CARD, null, null)));

    service.restore(1L, 1L);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.RESTORED, 1L));
  }

  @Test
  void delete_publishesDeletedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.delete(1L, 1L);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.DELETED, 1L));
  }

  @Test
  void restoreFromTrash_publishesRestoredEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.restoreFromTrash(9L, 1L);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.RESTORED, 1L));
  }

  @Test
  void purge_publishesDeletedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.purge(9L, 1L);

    // Seit #503 publiziert der Purge zusätzlich CardsPurgedEvent (Anhang-Aufräumkette).
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(events, times(2)).publishEvent(captor.capture());
    assertThat(captor.getAllValues())
        .containsExactly(
            new CardsPurgedEvent(List.of(1L)),
            new CardBoardActivityEvent(BOARD, ActivityType.DELETED, 1L));
  }

  @Test
  void purge_publishesCardsPurgedBeforeDeleting() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.purge(9L, 1L);

    // Reihenfolge ist die Zusage aus #503: Erst publizieren (Anhänge planen ihre Blob-Löschung
    // ein, solange die Metadaten existieren), dann löschen — die Cascade nimmt die Metadaten mit.
    InOrder inOrder = inOrder(events, cards);
    inOrder.verify(events).publishEvent(new CardsPurgedEvent(List.of(1L)));
    inOrder.verify(cards).deleteById(1L);
  }

  @Test
  void failedMutation_publishesNoEvent() {
    when(cards.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.archive(1L, 1L)).isInstanceOf(CardNotFoundException.class);

    verify(events, never()).publishEvent(any());
  }
}
