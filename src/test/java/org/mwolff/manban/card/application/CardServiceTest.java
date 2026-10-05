package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.board.application.ColumnNotFoundException;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.card.domain.Label;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.application.ProjectNotFoundException;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.context.ApplicationEventPublisher;

/** Verhaltenstests der Karten- und Epic-Use-Cases (Mockito an den Ports). */
// PMD.TooManyMethods: umfassende Unit-Suite (Karten + Epics, Erfolgs- und Fehlerpfade je
// Use-Case). Viele kleine @Test-Methoden sind hier gewollt, kein God-Class-Smell.
// PMD.ExcessiveImports entfiel mit den Tests zu Verschieben, Status und Umzug, die seit Issue #1395
// in CardMoveServiceTest stehen.
@SuppressWarnings({"PMD.TooManyMethods", "PMD.CyclomaticComplexity", "PMD.CouplingBetweenObjects"})
class CardServiceTest {

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
  private CardService service;

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

  private static ColumnView column(long id, String name, int position) {
    return new ColumnView(id, name, position, null);
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
        CardServiceAufbau.ausPorts(
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

  // --- create -----------------------------------------------------------

  @Test
  void create_setsCreatedAtFromInjectedClock() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.allocateActivePosition(20L)).thenReturn(0);

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.create(1L, BOARD, 20L, "Titel", null, null, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().createdAt()).isEqualTo(FIXED);
  }

  @Test
  void create_setsDueDate_whenProvided() {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.allocateActivePosition(20L)).thenReturn(0);
    Instant due = Instant.parse("2026-02-01T00:00:00Z");

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    // Die zurückgegebene View der Voll-Signatur (11 Args) wird bewusst geprüft, damit der
    // @Transactional-Einstieg (der an den privaten Kern doCreate delegiert) nicht null zurückgibt.
    CardView result =
        service.create(1L, BOARD, 20L, "Titel", null, null, null, due, null, null, null);

    verify(cards).save(captor.capture());
    assertThat(captor.getValue().dueDate()).isEqualTo(due);
    assertThat(result.dueDate()).isEqualTo(due);
  }

  @Test
  void create_appliesAssignees_atomically_withSingleCreatedActivity() {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.allocateActivePosition(20L)).thenReturn(0);
    when(permissions.isRealProjectMember(7L, 1L)).thenReturn(true);
    when(permissions.isRealProjectMember(8L, 1L)).thenReturn(true);

    service.create(
        1L, BOARD, 20L, "Titel", null, null, null, null, List.of(7L, 8L, 7L), null, null);

    verify(assignees).replaceAssignees(1L, List.of(7L, 8L));
    // Genau ein Aktivitätseintrag (CREATED) — kein zusätzlicher ASSIGNED beim atomaren Anlegen.
    verify(activity)
        .add(
            1L,
            1L,
            CardActivityType.CREATED,
            "Karte angelegt",
            FIXED,
            ActorContext.ActorStamp.unknown());
  }

  @Test
  void create_stampsActivityWithActorContext() {
    // Given — der Port liefert einen Token-Stempel; die Aktivität muss ihn unverändert tragen.
    ActorContext.ActorStamp stamp =
        new ActorContext.ActorStamp(
            org.mwolff.manban.card.domain.CardActivityOrigin.TOKEN, "Nachtlauf", "claude-opus-5");
    when(actor.current()).thenReturn(stamp);
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    service.create(1L, BOARD, 20L, "Titel", null, null, null);

    // Then
    verify(activity).add(1L, 1L, CardActivityType.CREATED, "Karte angelegt", FIXED, stamp);
    verify(activity, times(1)).add(anyLong(), anyLong(), any(), any(), any(), any());
  }

  @Test
  void create_ignoresEmptyAssignees() {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.allocateActivePosition(20L)).thenReturn(0);

    service.create(1L, BOARD, 20L, "Titel", null, null, null, null, List.of(), null, null);

    verify(assignees, never()).replaceAssignees(anyLong(), anyList());
  }

  @Test
  void create_rejectsForeignAssignee() {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.allocateActivePosition(20L)).thenReturn(0);
    when(permissions.isRealProjectMember(9L, 1L)).thenReturn(false);

    assertThatThrownBy(
            () ->
                service.create(
                    1L, BOARD, 20L, "Titel", null, null, null, null, List.of(9L), null, null))
        .isInstanceOf(InvalidAssigneeException.class);
    verify(assignees, never()).replaceAssignees(anyLong(), anyList());
  }

  @Test
  void create_appliesLabels_whenProvided() {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.allocateActivePosition(20L)).thenReturn(0);
    when(labels.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                new Label(7L, BOARD, "Bug", "#f00", false),
                new Label(8L, BOARD, "Ux", "#0f0", false)));

    service.create(
        1L, BOARD, 20L, "Titel", null, null, null, null, null, List.of(7L, 8L, 7L), null);

    verify(cardLabels).replaceLabels(1L, List.of(7L, 8L));
  }

  @Test
  void create_ignoresEmptyLabels() {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.allocateActivePosition(20L)).thenReturn(0);

    service.create(1L, BOARD, 20L, "Titel", null, null, null, null, null, List.of(), null);

    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  @Test
  void create_rejectsForeignLabel() {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.allocateActivePosition(20L)).thenReturn(0);
    when(labels.findByBoardId(BOARD))
        .thenReturn(List.of(new Label(7L, BOARD, "Bug", "#f00", false)));

    assertThatThrownBy(
            () ->
                service.create(
                    1L, BOARD, 20L, "Titel", null, null, null, null, null, List.of(8L), null))
        .isInstanceOf(InvalidLabelException.class);
    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  @Test
  void create_assignsNextBoardNumber() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(8);
    when(cards.allocateActivePosition(20L)).thenReturn(0);

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.create(1L, BOARD, 20L, "Titel", null, null, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().number()).isEqualTo(8);
  }

  @Test
  void create_appendsAtNextPositionInColumn() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.allocateActivePosition(20L)).thenReturn(5);

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.create(1L, BOARD, 20L, "Titel", null, null, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().positionInColumn()).isEqualTo(5);
  }

  @Test
  void create_trimsTitle() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.create(1L, BOARD, 20L, "  Titel  ", null, null, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().title()).isEqualTo("Titel");
  }

  @Test
  void create_attachesToParentEpic() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.findById(30L))
        .thenReturn(Optional.of(card(30L, 20L, 5, false, null, CardType.EPIC, null, "E")));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.create(1L, BOARD, 20L, "Titel", null, null, 30L);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().parentId()).isEqualTo(30L);
  }

  @Test
  void create_setsDependencies_whenProvided() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(5);
    when(cards.findByProjectId(PROJECT))
        .thenReturn(List.of(card(2L, 20L, 3, false, null, CardType.CARD, null, null)));

    // When
    service.create(1L, BOARD, 20L, "Titel", null, List.of(3, 3), null);

    // Then
    verify(dependencies).replaceDependencies(1L, List.of(3));
  }

  @Test
  void create_throwsBoardNotFound_whenBoardUnknown() {
    // Given
    when(boardService.requireProjectId(BOARD)).thenThrow(new BoardNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.create(1L, BOARD, 20L, "Titel", null, null, null))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void create_throwsColumnNotFound_whenColumnUnknown() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenThrow(new ColumnNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.create(1L, BOARD, 20L, "Titel", null, null, null))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  @Test
  void create_throwsColumnNotFound_whenColumnOnOtherBoard() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenThrow(new ColumnNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.create(1L, BOARD, 20L, "Titel", null, null, null))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  @Test
  void create_throwsInvalidDependency_whenParentIsNotEpic() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.findById(30L))
        .thenReturn(Optional.of(card(30L, 20L, 5, false, null, CardType.CARD, null, null)));

    // When / Then
    assertThatThrownBy(() -> service.create(1L, BOARD, 20L, "Titel", null, null, 30L))
        .isInstanceOf(InvalidDependencyException.class);
  }

  @Test
  void create_throwsCardNotFound_whenParentUnknown() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.findById(30L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.create(1L, BOARD, 20L, "Titel", null, null, 30L))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void create_throwsInvalidDependency_onSelfDependency() {
    // Given: die eigene Nummer 1 IST eine gültige Board-Nummer. So schlägt ein Umgehen des
    // Selbstbezug-Guards (Mutant) NICHT in „Unbekannte Nummer" um, sondern in einen Erfolg —
    // der Selbstbezug-Guard wird dadurch beweisbar geprüft.
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.findByProjectId(PROJECT))
        .thenReturn(List.of(card(9L, 20L, 1, false, null, CardType.CARD, null, null)));

    // When / Then: neue Karte bekommt Nummer 1, hängt von 1 (sich selbst) ab
    List<Integer> selfDependency = List.of(1);
    assertThatThrownBy(() -> service.create(1L, BOARD, 20L, "Titel", null, selfDependency, null))
        .isInstanceOf(InvalidDependencyException.class);
  }

  @Test
  void create_throwsInvalidDependency_onUnknownDependencyNumber() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.findByProjectId(PROJECT)).thenReturn(List.of());

    // When / Then
    List<Integer> unknownDependency = List.of(99);
    assertThatThrownBy(() -> service.create(1L, BOARD, 20L, "Titel", null, unknownDependency, null))
        .isInstanceOf(InvalidDependencyException.class);
  }

  // --- createEpic -------------------------------------------------------

  // --- listByBoard / listEpics -----------------------------------------

  @Test
  void listByBoard_returnsOnlyCards() {
    // Given
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                card(1L, 20L, 1, false, null, CardType.CARD, null, null),
                card(2L, 20L, 2, false, null, CardType.EPIC, null, "E")));

    // When
    List<CardView> result = service.listByBoard(1L, BOARD);

    // Then
    assertThat(result).singleElement().extracting(CardView::id).isEqualTo(1L);
  }

  // Die Sammelzugriffe von listByBoard (Issue #768) stehen in CardServiceListByBoardTest —
  // diese Klasse steht an ihren PMD-Grenzen (Groesse und Import-Zahl).

  @Test
  void listByBoard_throwsBoardNotFound_whenBoardUnknown() {
    // Given
    when(boardService.requireProjectId(BOARD)).thenThrow(new BoardNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.listByBoard(1L, BOARD))
        .isInstanceOf(BoardNotFoundException.class);
  }

  // --- update -----------------------------------------------------------

  @Test
  void update_setsCardParent() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(cards.findById(30L))
        .thenReturn(Optional.of(card(30L, 20L, 5, false, null, CardType.EPIC, null, "E")));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.update(1L, 1L, "Neu", null, null, null, 30L, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().parentId()).isEqualTo(30L);
  }

  @Test
  void update_setsEpicShortcode() {
    // Given
    when(cards.findById(5L))
        .thenReturn(Optional.of(card(5L, 20L, 5, false, null, CardType.EPIC, null, "old")));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.update(1L, 5L, "Neu", null, null, "NEW", null, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().shortcode()).isEqualTo("NEW");
  }

  @Test
  void update_replacesDependencies_whenProvided() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(cards.findByProjectId(PROJECT))
        .thenReturn(List.of(card(2L, 20L, 3, false, null, CardType.CARD, null, null)));

    // When
    service.update(1L, 1L, "Neu", null, List.of(3), null, null, null);

    // Then
    verify(dependencies).replaceDependencies(1L, List.of(3));
  }

  @Test
  void update_throwsCardNotFound_whenCardUnknown() {
    // Given
    when(cards.findById(1L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.update(1L, 1L, "Neu", null, null, null, null, null))
        .isInstanceOf(CardNotFoundException.class);
  }

  // --- assignParent -----------------------------------------------------

  // --- Randfälle: Zweigabdeckung ---------------------------------------

  @Test
  void create_throwsInvalidDependency_whenParentEpicOnOtherBoard() {
    // Given: Parent ist ein Epic, liegt aber auf einem anderen Board
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    Card epicOtherBoard =
        new Card(
            30L,
            99L,
            20L,
            5,
            "Epic",
            null,
            0,
            false,
            null,
            1L,
            FIXED,
            FIXED,
            CardType.EPIC,
            null,
            "E",
            null,
            PROJECT,
            null,
            null,
            null,
            null);
    when(cards.findById(30L)).thenReturn(Optional.of(epicOtherBoard));

    // When / Then
    assertThatThrownBy(() -> service.create(1L, BOARD, 20L, "Titel", null, null, 30L))
        .isInstanceOf(InvalidDependencyException.class);
  }

  @Test
  void create_clearsDependencies_whenEmptyList() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    service.create(1L, BOARD, 20L, "Titel", null, List.of(), null);

    // Then
    verify(dependencies).replaceDependencies(1L, List.of());
    // Eine leere Liste wird ohne Projekt-Lookup direkt geleert (Kurzschluss des isEmpty-Zweigs).
    // Ein Umgehen dieses Zweigs (Mutant) würde die projektweiten Nummern unnötig nachladen.
    verify(cards, never()).findByProjectId(PROJECT);
  }

  @Test
  void create_clearsDependencies_whenNullList() {
    // Given: dependsOn == null muss (wie leere Liste) die Abhängigkeiten leeren. Ein Umgehen
    // des null-Zweigs (Mutant) liefe in isEmpty() auf null und würde eine NPE werfen.
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    service.create(1L, BOARD, 20L, "Titel", null, null, null);

    // Then
    verify(dependencies).replaceDependencies(1L, List.of());
  }

  @Test
  void update_leavesDependenciesUntouched_whenDependsOnNull() {
    // Given: bei dependsOn == null darf update die Abhängigkeiten NICHT anfassen. Ein Umgehen
    // des null-Guards (Mutant) würde replaceDependencies aufrufen.
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    // When
    service.update(1L, 1L, "Neu", null, null, null, null, null);

    // Then
    verify(dependencies, never()).replaceDependencies(anyLong(), anyList());
  }

  @Test
  void create_normalizesBlankDescriptionToNull() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.create(1L, BOARD, 20L, "Titel", "   ", null, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().description()).isNull();
  }

  @Test
  void create_keepsNonBlankDescription() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.create(1L, BOARD, 20L, "Titel", "Beschreibung", null, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().description()).isEqualTo("Beschreibung");
  }

  // --- Rückgabe-/Interaktions-Verhalten (Issue #0073, Mutationsabdeckung) ----

  @Test
  void create_returnsViewOfPersistedCard() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    CardView view = service.create(1L, BOARD, 20L, "Titel", null, null, null);

    // Then
    assertThat(view.title()).isEqualTo("Titel");
  }

  @Test
  void update_returnsUpdatedView() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    // When
    CardView view = service.update(1L, 1L, "Neu", null, null, null, null, null);

    // Then
    assertThat(view.title()).isEqualTo("Neu");
  }

  // --- openEpicFromCard (Issue #640) ---------------------------------------

  // --- assignRequirement und Anforderung im EpicView (Issue #639) -----------

  @Test
  void update_setsDueDate_forCard() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    Instant due = FIXED.plusSeconds(86_400);

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    CardView view = service.update(1L, 1L, "Neu", null, null, null, null, due);

    verify(cards).save(captor.capture());
    assertThat(captor.getValue().dueDate()).isEqualTo(due);
    assertThat(view.dueDate()).isEqualTo(due);
  }

  // --- Zuständige (Assignees) -------------------------------------------

  @Test
  void setAssignees_replacesWithDistinctMembers() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(permissions.isRealProjectMember(7L, 1L)).thenReturn(true);
    when(permissions.isRealProjectMember(8L, 1L)).thenReturn(true);
    when(assignees.findByCardId(1L)).thenReturn(List.of(7L, 8L));

    CardView result = service.setAssignees(3L, 1L, List.of(7L, 8L, 7L));

    verify(permissions).require(3L, 1L, Permission.TICKET_UPDATE);
    verify(assignees).replaceAssignees(1L, List.of(7L, 8L));
    verify(activity)
        .add(
            1L,
            3L,
            CardActivityType.ASSIGNED,
            "Zuständige geändert",
            FIXED,
            ActorContext.ActorStamp.unknown());
    assertThat(result.id()).isEqualTo(1L);
    assertThat(result.assignees()).containsExactly(7L, 8L);
  }

  @Test
  void setAssignees_rejectsNonMember() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(permissions.isRealProjectMember(7L, 1L)).thenReturn(true);
    when(permissions.isRealProjectMember(8L, 1L)).thenReturn(false);

    assertThatThrownBy(() -> service.setAssignees(3L, 1L, List.of(7L, 8L)))
        .isInstanceOf(InvalidAssigneeException.class);
    verify(assignees, never()).replaceAssignees(anyLong(), anyList());
  }

  @Test
  void setAssignees_rejectsEpic() {
    when(cards.findById(5L))
        .thenReturn(Optional.of(card(5L, 20L, 5, false, null, CardType.EPIC, null, "E")));

    assertThatThrownBy(() -> service.setAssignees(3L, 5L, List.of(7L)))
        .isInstanceOf(InvalidDependencyException.class);
    verify(assignees, never()).replaceAssignees(anyLong(), anyList());
  }

  @Test
  void setAssignees_throwsCardNotFound_whenUnknown() {
    when(cards.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.setAssignees(3L, 1L, List.of()))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void setAssignees_propagatesPermissionDenied() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .require(9L, 1L, Permission.TICKET_UPDATE);

    assertThatThrownBy(() -> service.setAssignees(9L, 1L, List.of()))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(assignees, never()).replaceAssignees(anyLong(), anyList());
  }

  // --- Labels -----------------------------------------------------------

  @Test
  void setLabels_replacesWithDistinctBoardLabels() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(labels.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                new Label(7L, BOARD, "Bug", "#f00", false),
                new Label(8L, BOARD, "Ux", "#0f0", false)));
    when(cardLabels.findByCardId(1L)).thenReturn(List.of(7L, 8L));

    CardView view = service.setLabels(3L, 1L, List.of(7L, 8L, 7L));

    verify(permissions).require(3L, 1L, Permission.TICKET_UPDATE);
    verify(cardLabels).replaceLabels(1L, List.of(7L, 8L));
    assertThat(view.labels()).containsExactly(7L, 8L);
  }

  @Test
  void setLabels_rejectsForeignLabel() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(labels.findByBoardId(BOARD))
        .thenReturn(List.of(new Label(7L, BOARD, "Bug", "#f00", false)));

    assertThatThrownBy(() -> service.setLabels(3L, 1L, List.of(7L, 8L)))
        .isInstanceOf(InvalidLabelException.class);
    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  @Test
  void setLabels_rejectsEpic() {
    when(cards.findById(5L))
        .thenReturn(Optional.of(card(5L, 20L, 5, false, null, CardType.EPIC, null, "E")));

    assertThatThrownBy(() -> service.setLabels(3L, 5L, List.of(7L)))
        .isInstanceOf(InvalidDependencyException.class);
    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  @Test
  void setLabels_throwsCardNotFound_whenUnknown() {
    when(cards.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.setLabels(3L, 1L, List.of()))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void setLabels_propagatesPermissionDenied() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .require(9L, 1L, Permission.TICKET_UPDATE);

    assertThatThrownBy(() -> service.setLabels(9L, 1L, List.of()))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  // --- Labels an mehreren Karten (bulkLabels) ---------------------------

  /** Board-Labels für die Massenaktion: 7 liegt an den Karten, 9 ist das umzuschaltende. */
  private void zweiBoardLabels() {
    when(labels.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                new Label(7L, BOARD, "Bug", "#f00", false),
                new Label(9L, BOARD, "Nacht", "#00f", false)));
  }

  private void zweiKarten() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(cards.findById(2L))
        .thenReturn(Optional.of(card(2L, 20L, 2, false, null, CardType.CARD, null, null)));
  }

  @Test
  void bulkLabels_addsLabelToEveryCardAndKeepsTheOthers() {
    zweiKarten();
    zweiBoardLabels();
    // Zweimal je Karte gelesen: einmal für die neue Menge, einmal für die zurückgegebene Sicht.
    when(cardLabels.findByCardId(1L)).thenReturn(List.of(7L)).thenReturn(List.of(7L, 9L));
    when(cardLabels.findByCardId(2L)).thenReturn(List.of()).thenReturn(List.of(9L));

    List<CardView> result = service.bulkLabels(3L, List.of(1L, 2L), 9L, LabelAction.ADD);

    // Die Antwort trägt je Karte den neuen Stand — daran liest das Frontend die Labels ab.
    assertThat(result).extracting(CardView::id).containsExactly(1L, 2L);
    assertThat(result.get(0).labels()).containsExactly(7L, 9L);
    assertThat(result.get(1).labels()).containsExactly(9L);
    verify(permissions, times(2)).require(3L, PROJECT, Permission.TICKET_UPDATE);
    // Die übrigen Labels der Karte bleiben stehen — nur 9 kommt hinzu.
    verify(cardLabels).replaceLabels(1L, List.of(7L, 9L));
    verify(cardLabels).replaceLabels(2L, List.of(9L));
    verify(events).publishEvent(new CardBoardActivityEvent(BOARD, ActivityType.UPDATED, 1L));
    verify(events).publishEvent(new CardBoardActivityEvent(BOARD, ActivityType.UPDATED, 2L));
  }

  @Test
  void bulkLabels_removesLabelFromEveryCardAndKeepsTheOthers() {
    zweiKarten();
    zweiBoardLabels();
    when(cardLabels.findByCardId(1L)).thenReturn(List.of(7L, 9L));
    when(cardLabels.findByCardId(2L)).thenReturn(List.of(9L));

    service.bulkLabels(3L, List.of(1L, 2L), 9L, LabelAction.REMOVE);

    verify(cardLabels).replaceLabels(1L, List.of(7L));
    verify(cardLabels).replaceLabels(2L, List.of());
  }

  @Test
  void bulkLabels_addOnCardThatAlreadyCarriesItIsNoError() {
    zweiBoardLabels();
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(cardLabels.findByCardId(1L)).thenReturn(List.of(7L, 9L));

    assertThatCode(() -> service.bulkLabels(3L, List.of(1L), 9L, LabelAction.ADD))
        .doesNotThrowAnyException();
    verify(cardLabels).replaceLabels(1L, List.of(7L, 9L));
  }

  @Test
  void bulkLabels_removeOnCardWithoutItIsNoError() {
    zweiBoardLabels();
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(cardLabels.findByCardId(1L)).thenReturn(List.of(7L));

    assertThatCode(() -> service.bulkLabels(3L, List.of(1L), 9L, LabelAction.REMOVE))
        .doesNotThrowAnyException();
    verify(cardLabels).replaceLabels(1L, List.of(7L));
  }

  /**
   * Ein Vorhaben in der Auswahl lässt den ganzen Batch scheitern. Hier steht es an erster Stelle,
   * um ohne Transaktionsklammer zu zeigen, dass nichts geschrieben wurde; das echte Zurückrollen
   * einer gemischten Auswahl prüft {@code CardIT}.
   */
  @Test
  void bulkLabels_rejectsEpicInSelection() {
    when(cards.findById(5L))
        .thenReturn(Optional.of(card(5L, 20L, 5, false, null, CardType.EPIC, null, "E")));

    assertThatThrownBy(() -> service.bulkLabels(3L, List.of(5L, 1L), 9L, LabelAction.ADD))
        .isInstanceOf(InvalidDependencyException.class);
    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  /**
   * Auch beim Abnehmen wird geprüft, ob das Label zum Board der Karte gehört: Ein fremdes Label ist
   * an keiner Karte gesetzt, ein stiller Nicht-Treffer meldete also Erfolg für einen Aufruf, den
   * {@link CardService#setLabels} abwiese.
   */
  @Test
  void bulkLabels_rejectsForeignBoardLabelAlsoWhenRemoving() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(labels.findByBoardId(BOARD))
        .thenReturn(List.of(new Label(7L, BOARD, "Bug", "#f00", false)));

    assertThatThrownBy(() -> service.bulkLabels(3L, List.of(1L), 9L, LabelAction.REMOVE))
        .isInstanceOf(InvalidLabelException.class);
    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  @Test
  void bulkLabels_rejectsForeignBoardLabelWhenAdding() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(labels.findByBoardId(BOARD))
        .thenReturn(List.of(new Label(7L, BOARD, "Bug", "#f00", false)));

    assertThatThrownBy(() -> service.bulkLabels(3L, List.of(1L), 9L, LabelAction.ADD))
        .isInstanceOf(InvalidLabelException.class);
    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  @Test
  void bulkLabels_throwsCardNotFound_whenUnknown() {
    when(cards.findById(2L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.bulkLabels(3L, List.of(2L, 1L), 9L, LabelAction.ADD))
        .isInstanceOf(CardNotFoundException.class);
    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  @Test
  void bulkLabels_propagatesPermissionDenied() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .require(9L, PROJECT, Permission.TICKET_UPDATE);

    assertThatThrownBy(() -> service.bulkLabels(9L, List.of(1L, 2L), 9L, LabelAction.ADD))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  // --- Aktivitätsverlauf (card_activity) --------------------------------

  @Test
  void create_recordsCreatedActivity() {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    service.create(1L, BOARD, 20L, "Titel", null, null, null);

    verify(activity)
        .add(
            1L,
            1L,
            CardActivityType.CREATED,
            "Karte angelegt",
            FIXED,
            ActorContext.ActorStamp.unknown());
  }

  @Test
  void update_recordsUpdatedActivity() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.update(9L, 1L, "Neu", null, null, null, null, null);

    verify(activity)
        .add(
            1L,
            9L,
            CardActivityType.UPDATED,
            "Karte bearbeitet",
            FIXED,
            ActorContext.ActorStamp.unknown());
  }

  @Test
  void listActivity_returnsHistoryForMember() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    CardActivity entry =
        new CardActivity(
            3L, 1L, 9L, CardActivityType.CREATED, "Karte angelegt", FIXED, null, null, null);
    when(activity.findByCardId(1L)).thenReturn(List.of(entry));

    List<CardActivity> result = service.listActivity(5L, 1L);

    verify(permissions).requireMembership(5L, 1L);
    assertThat(result).containsExactly(entry);
  }

  @Test
  void listActivity_throwsCardNotFound_whenUnknown() {
    when(cards.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.listActivity(5L, 1L))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void listActivityViews_mapsDomainToFacadeView() {
    // Given: die Fassaden-Sicht ist der Weg, auf dem fremde Module den Verlauf lesen, ohne
    // card.domain zu importieren (#876).
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    CardActivity entry =
        new CardActivity(
            3L,
            1L,
            9L,
            CardActivityType.MOVED,
            "Verschoben",
            FIXED,
            CardActivityOrigin.TOKEN,
            "Nachtlauf",
            "claude-opus-5");
    when(activity.findByCardId(1L)).thenReturn(List.of(entry));

    // When
    List<CardService.ActivityView> result = service.listActivityViews(5L, 1L);

    // Then: dieselbe Rechteprüfung wie listActivity, alle Felder unverändert übernommen
    verify(permissions).requireMembership(5L, PROJECT);
    assertThat(result)
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v.id()).isEqualTo(3L);
              assertThat(v.actorUserId()).isEqualTo(9L);
              assertThat(v.type()).isEqualTo("MOVED");
              assertThat(v.detail()).isEqualTo("Verschoben");
              assertThat(v.createdAt()).isEqualTo(FIXED);
              assertThat(v.origin()).isEqualTo("TOKEN");
              assertThat(v.tokenName()).isEqualTo("Nachtlauf");
              assertThat(v.agent()).isEqualTo("claude-opus-5");
            });
  }

  @Test
  void listActivityViews_mapsLegacyEntryWithoutOrigin() {
    // Given: Alt-Eintrag vor V23 — die Sicht trägt null statt eines Platzhalters.
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(activity.findByCardId(1L))
        .thenReturn(
            List.of(
                new CardActivity(
                    3L, 1L, null, CardActivityType.CREATED, "Angelegt", FIXED, null, null, null)));

    // When
    List<CardService.ActivityView> result = service.listActivityViews(5L, 1L);

    // Then
    assertThat(result)
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v.actorUserId()).isNull();
              assertThat(v.origin()).isNull();
              assertThat(v.tokenName()).isNull();
              assertThat(v.agent()).isNull();
            });
  }

  @Test
  void listActivityViews_throwsCardNotFound_whenUnknown() {
    when(cards.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.listActivityViews(5L, 1L))
        .isInstanceOf(CardNotFoundException.class);
  }

  // --- Zykluszeit-Tracking (card_column_transition) ---------------------

  @Test
  void create_opensColumnTransition() {
    // Given
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
    when(cards.allocateActivePosition(20L)).thenReturn(0);

    // When
    service.create(1L, BOARD, 20L, "Titel", null, null, null);

    // Then — Eintritt in die Zielspalte wird mit dem Erstellzeitpunkt eröffnet.
    verify(transitions).open(1L, 20L, "Backlog", FIXED);
  }

  // --- Live-Board-Events (#342): je board-relevanter Mutation ein BoardChangedEvent ------------

  private CardBoardActivityEvent onlyPublishedEvent() {
    ArgumentCaptor<CardBoardActivityEvent> captor =
        ArgumentCaptor.forClass(CardBoardActivityEvent.class);
    verify(events).publishEvent(captor.capture());
    return captor.getValue();
  }

  @Test
  void create_publishesCreatedEvent() {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    service.create(1L, BOARD, 20L, "Titel", null, null, null);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.CREATED, 1L));
  }

  @Test
  void update_publishesUpdatedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.update(1L, 1L, "Neu", null, null, null, null, null);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.UPDATED, 1L));
  }

  @Test
  void setAssignees_publishesUpdatedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.setAssignees(3L, 1L, List.of());

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.UPDATED, 1L));
  }

  @Test
  void setLabels_publishesUpdatedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.setLabels(3L, 1L, List.of());

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.UPDATED, 1L));
  }

  // --- Modul-Fassade fuer fremde Module (#458) --------------------------

  /** Board-gebundene Karte mit frei waehlbarer Position, Sichtbarkeit und Typ. */
  private static Card boardCard(
      long id, long columnId, int number, int position, boolean archived) {
    return new Card(
        id,
        BOARD,
        columnId,
        number,
        "Titel",
        "Body",
        position,
        archived,
        null,
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
        null);
  }

  @Test
  void requireProjectId_returnsProjectOfCard() {
    when(cards.findById(1L)).thenReturn(Optional.of(boardCard(1L, 20L, 1, 0, false)));

    assertThat(service.requireProjectId(1L)).isEqualTo(PROJECT);
  }

  @Test
  void requireProjectId_throwsCardNotFound_whenCardUnknown() {
    when(cards.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.requireProjectId(1L))
        .isInstanceOf(CardNotFoundException.class);
  }

  // --- getCard ---------------------------------------------------------

  @Test
  void getCard_returnsViewOfCard() {
    when(cards.findById(1L)).thenReturn(Optional.of(boardCard(1L, 20L, 7, 0, false)));

    assertThat(service.getCard(5L, 1L)).extracting(CardView::number).isEqualTo(7);
  }

  @Test
  void getCard_liefertDieVolleBeschreibungOhneAuszug() {
    // Issue #771: Der Auszug gehoert der Board-Liste; der Einzelabruf bleibt die Quelle des
    // Volltexts. Waere hier beides gesetzt, gaebe es zwei Wahrheiten fuer denselben Text.
    when(cards.findById(1L)).thenReturn(Optional.of(boardCard(1L, 20L, 7, 0, false)));

    CardView sicht = service.getCard(5L, 1L);

    assertThat(sicht.description()).isEqualTo("Body");
    assertThat(sicht.excerpt()).isNull();
  }

  @Test
  void getCard_requiresMembershipInCardsProject() {
    when(cards.findById(1L)).thenReturn(Optional.of(boardCard(1L, 20L, 7, 0, false)));

    service.getCard(5L, 1L);

    verify(permissions).requireMembership(5L, PROJECT);
  }

  @Test
  void getCard_liefertDieHerkunftAlsNummerDesVorfahren() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(boardCard(1L, 20L, 7, 0, false).withDerivedFrom(91L)));
    when(cards.findById(91L))
        .thenReturn(Optional.of(card(91L, 20L, 42, false, null, CardType.CARD, null, null)));

    assertThat(service.getCard(5L, 1L).derivedFrom()).isEqualTo(42);
  }

  @Test
  void getCard_liefertNull_wennDerVorfahrNichtMehrExistiert() {
    // Regulaer raeumt ON DELETE SET NULL das auf; die Sicht haelt den Zustand trotzdem aus.
    when(cards.findById(1L))
        .thenReturn(Optional.of(boardCard(1L, 20L, 7, 0, false).withDerivedFrom(91L)));
    when(cards.findById(91L)).thenReturn(Optional.empty());

    assertThat(service.getCard(5L, 1L).derivedFrom()).isNull();
  }

  @Test
  void getCard_liefertNull_ohneHerkunft() {
    when(cards.findById(1L)).thenReturn(Optional.of(boardCard(1L, 20L, 7, 0, false)));

    assertThat(service.getCard(5L, 1L).derivedFrom()).isNull();
  }

  @Test
  void getCard_throwsCardNotFound_whenCardUnknown() {
    when(cards.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getCard(5L, 1L)).isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void getCard_propagatesNotFound_whenCallerIsNoMember() {
    // Nichtmitglied wie unbekannte Karte → 404, kein Existenz-Leak.
    when(cards.findById(1L)).thenReturn(Optional.of(boardCard(1L, 20L, 7, 0, false)));
    doThrow(new ProjectNotFoundException()).when(permissions).requireMembership(5L, PROJECT);

    assertThatThrownBy(() -> service.getCard(5L, 1L)).isInstanceOf(ProjectNotFoundException.class);
  }

  private static Card paket(long id, String titel, CardType type, @Nullable CardStatus status) {
    return new Card(
        id, BOARD, 20L, 7, titel, null, 3, false, null, 1L, FIXED, FIXED, type, null, null, null,
        PROJECT, null, null, null, status);
  }

  // --- status und canSetStatus in der Sicht (Issue #1300, E8/E10) ------

  @Test
  void getCard_traegtStatusUndCanSetStatusAusDerCardMovePruefung() {
    when(cards.findById(5L))
        .thenReturn(Optional.of(paket(5L, "Paket", CardType.CARD, CardStatus.IN_REVIEW)));
    when(permissions.hasPermission(1L, PROJECT, Permission.CARD_MOVE)).thenReturn(true);

    CardView sicht = service.getCard(1L, 5L);

    assertThat(sicht.status()).isEqualTo("IN_REVIEW");
    assertThat(sicht.canSetStatus()).isTrue();
  }

  @Test
  void getCard_ohneCardMove_canSetStatusFalsch() {
    when(cards.findById(5L))
        .thenReturn(Optional.of(paket(5L, "Paket", CardType.CARD, CardStatus.IN_REVIEW)));
    when(permissions.hasPermission(1L, PROJECT, Permission.CARD_MOVE)).thenReturn(false);

    assertThat(service.getCard(1L, 5L).canSetStatus()).isFalse();
  }

  @Test
  void getCard_ohneEigenenStatus_statusNullUndCanSetStatusFalsch() {
    // Ohne eigenen Status gibt es nichts zu setzen — auch nicht für jemanden mit CARD_MOVE.
    when(cards.findById(5L)).thenReturn(Optional.of(paket(5L, "[Plan] Plan", CardType.CARD, null)));
    when(permissions.hasPermission(1L, PROJECT, Permission.CARD_MOVE)).thenReturn(true);

    CardView sicht = service.getCard(1L, 5L);

    assertThat(sicht.status()).isNull();
    assertThat(sicht.canSetStatus()).isFalse();
  }

  @Test
  void listByBoard_prueftCardMoveEinmalFuerAlleKarten() {
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                paket(1L, "A", CardType.CARD, CardStatus.READY),
                paket(2L, "B", CardType.CARD, CardStatus.DONE),
                paket(3L, "[Idee] C", CardType.CARD, null)));
    when(permissions.hasPermission(1L, PROJECT, Permission.CARD_MOVE)).thenReturn(true);

    List<CardView> sichten = service.listByBoard(1L, BOARD);

    // Die Dokumentkarte trägt keinen Status — also auch kein Recht, ihn zu setzen.
    assertThat(sichten)
        .extracting(CardView::status, CardView::canSetStatus)
        .containsExactly(tuple("READY", true), tuple("DONE", true), tuple(null, false));
    verify(permissions, times(1)).hasPermission(1L, PROJECT, Permission.CARD_MOVE);
  }

  @Test
  void listByBoard_ohneCardMove_canSetStatusFalsch() {
    when(cards.findByBoardId(BOARD))
        .thenReturn(List.of(paket(1L, "A", CardType.CARD, CardStatus.READY)));
    when(permissions.hasPermission(1L, PROJECT, Permission.CARD_MOVE)).thenReturn(false);

    assertThat(service.listByBoard(1L, BOARD))
        .extracting(CardView::status, CardView::canSetStatus)
        .containsExactly(tuple("READY", false));
  }
}
