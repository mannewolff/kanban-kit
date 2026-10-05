package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.board.application.ColumnNotFoundException;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Verhaltenstests von Verschieben, Status und Umzug (Mockito an den Ports). Die Testmethoden
 * stammen unverändert aus {@code CardServiceTest} (Issue #1395, Plan #1387 E6); {@link
 * KartenGrundlage}, {@link KartenSicht} und {@link KartenAbhaengigkeiten} entstehen echt aus
 * denselben Port-Mocks.
 */
// PMD.TooManyMethods: wandert mit den Testmethoden aus CardServiceTest (Issue #1395) — je
// Verschiebe-, Status- und Umzugsregel ein kleiner @Test, Erfolgs- und Fehlerpfad getrennt.
// PMD.CouplingBetweenObjects: der Prüfling entsteht aus den Ports, die KartenGrundlage und
// KartenSicht echt brauchen (Plan #1387, E6), wie in CardArchiveServiceTest. Ein Mock der
// Bausteine senkte die Kopplung, ließe Abdeckung und Mutationsprüfung der Helfer aber ins Leere
// laufen.
@SuppressWarnings({"PMD.TooManyMethods", "PMD.CouplingBetweenObjects"})
class CardMoveServiceTest {

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
  private CardMoveService service;

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
        CardServiceAufbau.moveAusPorts(
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

  // --- move -------------------------------------------------------------

  @Test
  void move_setsMovedToDoneAt_whenEnteringDoneColumn() {
    // Given
    Card before = card(1L, 20L, 1, false, null, CardType.CARD, null, null);
    when(cards.findById(1L)).thenReturn(Optional.of(before));
    when(boardService.requireColumn(21L, BOARD)).thenReturn(column(21L, "Done", 4));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.move(1L, 1L, 21L, 0);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().movedToDoneAt()).isEqualTo(FIXED);
  }

  @Test
  void move_clearsMovedToDoneAt_whenLeavingDoneColumn() {
    // Given
    Card before = card(1L, 21L, 1, false, FIXED.minusSeconds(10), CardType.CARD, null, null);
    when(cards.findById(1L)).thenReturn(Optional.of(before));
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.move(1L, 1L, 20L, 0);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().movedToDoneAt()).isNull();
  }

  @Test
  void move_throwsCardNotFound_whenCardUnknown() {
    // Given
    when(cards.findById(1L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.move(1L, 1L, 20L, 0))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void move_throwsInvalidDependency_forEpic() {
    // Given
    when(cards.findById(5L))
        .thenReturn(Optional.of(card(5L, 20L, 5, false, null, CardType.EPIC, null, "E")));

    // When / Then
    assertThatThrownBy(() -> service.move(1L, 5L, 20L, 0))
        .isInstanceOf(InvalidDependencyException.class);
  }

  @Test
  void move_throwsColumnNotFound_whenTargetUnknown() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(boardService.requireColumn(21L, BOARD)).thenThrow(new ColumnNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.move(1L, 1L, 21L, 0))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  @Test
  void move_throwsColumnNotFound_whenTargetOnOtherBoard() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(boardService.requireColumn(21L, BOARD)).thenThrow(new ColumnNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.move(1L, 1L, 21L, 0))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  @Test
  void move_throwsBoardNotFound_whenBoardUnknown() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(boardService.requireProjectId(BOARD)).thenThrow(new BoardNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.move(1L, 1L, 21L, 0))
        .isInstanceOf(BoardNotFoundException.class);
  }

  // --- sortColumnByNumber ------------------------------------------------

  @Test
  void sortColumnByNumber_requiresCardMove_andDelegatesAscending() {
    // Given
    when(boardService.boardIdOfColumn(20L)).thenReturn(BOARD);

    // When
    service.sortColumnByNumber(1L, 20L, SortDirection.ASC);

    // Then
    verify(permissions).require(1L, PROJECT, Permission.CARD_MOVE);
    verify(cards).sortActiveByNumber(20L, SortDirection.ASC);
  }

  @Test
  void sortColumnByNumber_delegatesDescending() {
    // Given
    when(boardService.boardIdOfColumn(20L)).thenReturn(BOARD);

    // When
    service.sortColumnByNumber(1L, 20L, SortDirection.DESC);

    // Then
    verify(cards).sortActiveByNumber(20L, SortDirection.DESC);
  }

  @Test
  void sortColumnByNumber_publishesBoardChangedEvent() {
    // Given
    when(boardService.boardIdOfColumn(20L)).thenReturn(BOARD);

    // When
    service.sortColumnByNumber(1L, 20L, SortDirection.ASC);

    // Then: offene Boards ziehen über SSE nach
    verify(events).publishEvent(new CardBoardActivityEvent(BOARD, ActivityType.MOVED, null));
  }

  @Test
  void sortColumnByNumber_throwsColumnNotFound_whenColumnUnknown() {
    // Given
    when(boardService.boardIdOfColumn(20L)).thenThrow(new ColumnNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.sortColumnByNumber(1L, 20L, SortDirection.ASC))
        .isInstanceOf(ColumnNotFoundException.class);
    verify(cards, never()).sortActiveByNumber(anyLong(), any(SortDirection.class));
    verify(events, never()).publishEvent(any());
  }

  @Test
  void sortColumnByNumber_propagatesPermissionDenied() {
    // Given
    when(boardService.boardIdOfColumn(20L)).thenReturn(BOARD);
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .require(9L, PROJECT, Permission.CARD_MOVE);

    // When / Then
    assertThatThrownBy(() -> service.sortColumnByNumber(9L, 20L, SortDirection.ASC))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(cards, never()).sortActiveByNumber(anyLong(), any(SortDirection.class));
    verify(events, never()).publishEvent(any());
  }

  // --- Randfälle: Zweigabdeckung ---------------------------------------

  @Test
  void move_keepsMovedToDoneAt_whenStayingInDoneColumn() {
    // Given: Karte ist bereits "done" und wechselt in eine andere Done-Spalte
    Instant earlier = FIXED.minusSeconds(10);
    Card before = card(1L, 104L, 1, false, earlier, CardType.CARD, null, null);
    when(cards.findById(1L)).thenReturn(Optional.of(before));
    when(boardService.requireColumn(21L, BOARD)).thenReturn(column(21L, "Done", 4));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.move(1L, 1L, 21L, 0);

    // Then: der ursprüngliche Done-Zeitpunkt bleibt erhalten
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().movedToDoneAt()).isEqualTo(earlier);
  }

  @Test
  void move_treatsNullColumnNameAsNotDone() {
    // Given: Ziel-Spalte ohne Namen -> gilt nicht als Done
    Card before = card(1L, 20L, 1, false, null, CardType.CARD, null, null);
    when(cards.findById(1L)).thenReturn(Optional.of(before));
    when(boardService.requireColumn(22L, BOARD)).thenReturn(column(22L, null, 5));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.move(1L, 1L, 22L, 0);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().movedToDoneAt()).isNull();
  }

  // --- Rückgabe-/Interaktions-Verhalten (Issue #0073, Mutationsabdeckung) ----

  @Test
  void move_persistsMoveViaRepository() {
    // Given
    Card before = card(1L, 20L, 1, false, null, CardType.CARD, null, null);
    when(cards.findById(1L)).thenReturn(Optional.of(before));
    when(boardService.requireColumn(21L, BOARD)).thenReturn(column(21L, "Done", 4));

    // When
    service.move(1L, 1L, 21L, 3);

    // Then
    verify(cards).move(1L, 21L, 3);
  }

  @Test
  void move_returnsViewOfMovedCard() {
    // Given
    Card before = card(1L, 20L, 1, false, null, CardType.CARD, null, null);
    when(cards.findById(1L)).thenReturn(Optional.of(before));
    when(boardService.requireColumn(21L, BOARD)).thenReturn(column(21L, "Done", 4));

    // When
    CardView view = service.move(1L, 1L, 21L, 0);

    // Then
    assertThat(view.id()).isEqualTo(1L);
  }

  // --- transfer (board-/projektübergreifend) ----------------------------

  /**
   * Stubbt Karte, Ziel-Board (Projekt 2) und Ziel-Spalte für einen Transfer und liefert die Karte.
   */
  private void stubTransferScenario(Long parentId) {
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, FIXED, CardType.CARD, parentId, null)));
    when(boardService.requireProjectId(20L)).thenReturn(2L);
    when(boardService.requireColumn(60L, 20L)).thenReturn(new ColumnView(60L, "Backlog", 0, null));
    when(cards.allocateCardNumber(2L)).thenReturn(8);
  }

  @Test
  void transfer_movesCardToTargetBoardWithNextNumber() {
    // Given
    stubTransferScenario(9L);

    // When
    CardView view = service.transfer(1L, 100L, 20L, 60L);

    // Then
    verify(cards).transfer(100L, 20L, 60L, 8);
    assertThat(view.id()).isEqualTo(100L);
  }

  @Test
  void transfer_projektwechsel_loeschtDieHerkunftDerKarte() {
    stubTransferScenario(9L);
    when(cards.findById(100L))
        .thenReturn(
            Optional.of(
                withHerkunft(card(100L, 50L, 3, false, FIXED, CardType.CARD, 9L, null), 77L)));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.transfer(1L, 100L, 20L, 60L);

    verify(cards).save(captor.capture());
    assertThat(captor.getValue().derivedFromCardId()).isNull();
  }

  @Test
  void transfer_projektwechsel_loeschtDieHerkunftDerKinder() {
    stubTransferScenario(9L);
    Card kind = withHerkunft(card(200L, 50L, 4, false, null, CardType.CARD, null, null), 100L);
    Card archiviertesKind =
        withHerkunft(card(201L, 50L, 5, true, null, CardType.CARD, null, null), 100L);
    when(cards.findByDerivedFrom(100L)).thenReturn(List.of(kind, archiviertesKind));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.transfer(1L, 100L, 20L, 60L);

    verify(cards, times(3)).save(captor.capture());
    assertThat(captor.getAllValues())
        .filteredOn(c -> c.requireId() == 200L || c.requireId() == 201L)
        .hasSize(2)
        .allSatisfy(c -> assertThat(c.derivedFromCardId()).isNull());
  }

  @Test
  void transfer_innerhalbDesProjekts_laesstDieHerkunftUnberuehrt() {
    // Ziel-Board liegt im SELBEN Projekt: Die Kette ueberlebt den Board-Wechsel.
    when(cards.findById(100L))
        .thenReturn(
            Optional.of(
                withHerkunft(card(100L, 50L, 3, false, FIXED, CardType.CARD, 9L, null), 77L)));
    when(boardService.requireProjectId(20L)).thenReturn(PROJECT);
    when(boardService.requireColumn(60L, 20L)).thenReturn(new ColumnView(60L, "Backlog", 0, null));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.transfer(1L, 100L, 20L, 60L);

    verify(cards).save(captor.capture());
    assertThat(captor.getValue().derivedFromCardId()).isEqualTo(77L);
    verify(cards, never()).findByDerivedFrom(anyLong());
  }

  /**
   * Setzt die Herkunft auf einer Testkarte — der Record-Wither bleibt die einzige Schreibstelle.
   */
  private static Card withHerkunft(Card c, long herkunft) {
    return c.withDerivedFrom(herkunft);
  }

  @Test
  void transfer_projektwechsel_loeschtDieAnforderungDerKarte() {
    stubTransferScenario(9L);
    when(cards.findById(100L))
        .thenReturn(
            Optional.of(
                card(100L, 50L, 3, false, FIXED, CardType.CARD, 9L, null).withRequirement(77L)));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.transfer(1L, 100L, 20L, 60L);

    verify(cards).save(captor.capture());
    assertThat(captor.getValue().requirementCardId()).isNull();
  }

  /**
   * Gegenrichtung: Wandert die Anforderungskarte ab, verliert das zurueckbleibende Vorhaben seinen
   * Verweis. Ohne das zeigte er ueber die Projektgrenze auf eine Nummer, die dort einer anderen
   * Karte gehoert.
   */
  @Test
  void transfer_projektwechsel_loeschtDenVerweisDerZeigendenVorhaben() {
    stubTransferScenario(9L);
    Card vorhaben =
        card(300L, 50L, 6, false, null, CardType.EPIC, null, null).withRequirement(100L);
    when(cards.findByRequirementCard(100L)).thenReturn(List.of(vorhaben));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.transfer(1L, 100L, 20L, 60L);

    verify(cards, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .filteredOn(c -> c.requireId() == 300L)
        .singleElement()
        .satisfies(c -> assertThat(c.requirementCardId()).isNull());
  }

  @Test
  void transfer_innerhalbDesProjekts_laesstDieAnforderungUnberuehrt() {
    // Ziel-Board im SELBEN Projekt: Die Zuordnung ueberlebt den Board-Wechsel.
    when(cards.findById(100L))
        .thenReturn(
            Optional.of(
                card(100L, 50L, 3, false, FIXED, CardType.CARD, 9L, null).withRequirement(77L)));
    when(boardService.requireProjectId(20L)).thenReturn(PROJECT);
    when(boardService.requireColumn(60L, 20L)).thenReturn(new ColumnView(60L, "Backlog", 0, null));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.transfer(1L, 100L, 20L, 60L);

    verify(cards).save(captor.capture());
    assertThat(captor.getValue().requirementCardId()).isEqualTo(77L);
    verify(cards, never()).findByRequirementCard(anyLong());
  }

  @Test
  void bulkTransfer_loeschtDieHerkunftAuchWennVorfahrUndKindZusammenWandern() {
    // Wandern Vorfahr (100) und Kind (200) im selben Batch ins selbe Zielprojekt, waere die
    // Beziehung dort eigentlich weiter konsistent — sie wird trotzdem geloescht. Eine
    // Batch-Ausnahme haette das Ergebnis von der Reihenfolge innerhalb des Batches abhaengig
    // gemacht.
    stubTransferScenario(null);
    Card kind = withHerkunft(card(200L, 50L, 4, false, null, CardType.CARD, null, null), 100L);
    when(cards.findById(200L)).thenReturn(Optional.of(kind));
    when(cards.findByDerivedFrom(100L)).thenReturn(List.of(kind));
    when(cards.allocateCardNumber(2L)).thenReturn(8, 9);

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.bulkTransfer(1L, List.of(100L, 200L), 20L, 60L);

    verify(cards, times(3)).save(captor.capture());
    assertThat(captor.getAllValues())
        .filteredOn(c -> c.requireId() == 200L)
        .isNotEmpty()
        .allSatisfy(c -> assertThat(c.derivedFromCardId()).isNull());
  }

  @Test
  void transfer_clearsParentAndDependencies() {
    // Given
    stubTransferScenario(9L);

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.transfer(1L, 100L, 20L, 60L);

    // Then
    verify(dependencies).deleteByCardId(100L);
    verify(assignees).deleteByCardId(100L);
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().parentId()).isNull();
    assertThat(captor.getValue().movedToDoneAt()).isNull();
  }

  @Test
  void transfer_sameProject_keepsNumberAndKeepsDependenciesAndAssignees() {
    // Given: Ziel-Board liegt im SELBEN Projekt (1) wie die Quelle (card 100 hat projectId 1).
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, FIXED, CardType.CARD, 9L, null)));
    when(boardService.requireProjectId(20L)).thenReturn(1L);
    when(boardService.requireColumn(60L, 20L)).thenReturn(new ColumnView(60L, "Backlog", 0, null));

    // When
    service.transfer(1L, 100L, 20L, 60L);

    // Then: die Nummer (3) bleibt erhalten — keine Neuvergabe, keine Nummernvergabe …
    verify(cards).transfer(100L, 20L, 60L, 3);
    verify(cards, never()).allocateCardNumber(anyLong());
    // … und projekt-lokale Verknüpfungen wandern mit (werden NICHT gelöscht).
    verify(dependencies, never()).deleteByCardId(anyLong());
    verify(assignees, never()).deleteByCardId(anyLong());
  }

  @Test
  void transfer_acrossProjects_requiresOwnerInBothProjects() {
    // Given
    stubTransferScenario(null);

    // When
    service.transfer(1L, 100L, 20L, 60L);

    // Then — Quellprojekt (1) und Zielprojekt (2); das Verschieberecht genügt hier nicht
    verify(permissions).requireOwner(1L, 1L);
    verify(permissions).requireOwner(1L, 2L);
    verify(permissions, never()).require(anyLong(), anyLong(), any(Permission.class));
  }

  /**
   * Der Projektwechsel verlangt OWNER in <b>beiden</b> Projekten. Seit Issue #1165 trägt die Matrix
   * dafür den Schlüssel {@code CARD_MOVE_PROJECT}; er beschreibt die Regel, ersetzt sie aber nicht.
   * Reicht die Rolle nur im Quellprojekt, bleibt die Karte, wo sie ist — die Projektgrenze ist die
   * Vertraulichkeitsgrenze.
   */
  @Test
  void transfer_acrossProjects_scheitertWennOwnerNurImQuellprojektGilt() {
    // Given: Zielprojekt (2) verweigert; das Quellprojekt (0) lässt durch.
    stubTransferScenario(null);
    doThrow(new ProjectAccessDeniedException()).when(permissions).requireOwner(1L, 2L);

    // When / Then
    assertThatThrownBy(() -> service.transfer(1L, 100L, 20L, 60L))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(cards, never()).transfer(anyLong(), anyLong(), anyLong(), anyInt());
    verify(dependencies, never()).deleteByCardId(anyLong());
  }

  @Test
  void transfer_sameProject_requiresCardMoveInsteadOfOwner() {
    // Given: Ziel-Board liegt im SELBEN Projekt (1) wie die Quelle.
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, FIXED, CardType.CARD, null, null)));
    when(boardService.requireProjectId(20L)).thenReturn(1L);
    when(boardService.requireColumn(60L, 20L)).thenReturn(new ColumnView(60L, "Backlog", 0, null));

    // When
    service.transfer(1L, 100L, 20L, 60L);

    // Then — projektintern genügt das Verschieberecht, Eigentümer wird nicht verlangt
    verify(permissions).require(1L, 1L, Permission.CARD_MOVE);
    verify(permissions, never()).requireOwner(anyLong(), anyLong());
  }

  @Test
  void transfer_rejectsEpic() {
    // Given
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, null, CardType.EPIC, null, "EP")));

    // When / Then
    assertThatThrownBy(() -> service.transfer(1L, 100L, 20L, 60L))
        .isInstanceOf(InvalidDependencyException.class);
    verify(cards, never()).transfer(anyLong(), anyLong(), anyLong(), anyInt());
  }

  @Test
  void transfer_throwsCardNotFound_whenUnknown() {
    // Given
    when(cards.findById(100L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.transfer(1L, 100L, 20L, 60L))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void bulkTransfer_transfersEveryCardToTarget() {
    // Given: zwei Karten, gemeinsames Zielboard/-spalte
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, FIXED, CardType.CARD, null, null)));
    when(cards.findById(101L))
        .thenReturn(Optional.of(card(101L, 50L, 4, false, FIXED, CardType.CARD, null, null)));
    when(boardService.requireProjectId(20L)).thenReturn(2L);
    when(boardService.requireColumn(60L, 20L)).thenReturn(new ColumnView(60L, "Backlog", 0, null));
    when(cards.allocateCardNumber(2L)).thenReturn(8);

    // When
    List<CardView> result = service.bulkTransfer(1L, List.of(100L, 101L), 20L, 60L);

    // Then — die Views je Karte werden zurückgegeben (nicht null)
    assertThat(result).extracting(CardView::id).containsExactly(100L, 101L);
    verify(cards).transfer(100L, 20L, 60L, 8);
    verify(cards).transfer(101L, 20L, 60L, 8);
  }

  @Test
  void bulkTransfer_locksTargetAndAllSourceColumnsInOneGo() {
    // Given: zwei Karten aus verschiedenen Quellspalten
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, FIXED, CardType.CARD, null, null)));
    when(cards.findById(101L))
        .thenReturn(Optional.of(card(101L, 51L, 4, false, FIXED, CardType.CARD, null, null)));
    when(boardService.requireProjectId(20L)).thenReturn(2L);
    when(boardService.requireColumn(60L, 20L)).thenReturn(new ColumnView(60L, "Backlog", 0, null));
    when(cards.allocateCardNumber(2L)).thenReturn(8);

    // When
    service.bulkTransfer(1L, List.of(100L, 101L), 20L, 60L);

    // Then: Zielspalte und beide Quellspalten in einem einzigen Sperraufruf — nähme jeder
    // Einzel-Umzug seine Sperren für sich, könnten zwei Sammel-Umzüge sie über Kreuz greifen
    // und verklemmen (#499). Das Zielprojekt (2) ist ein anderes als das der Karten (1), also
    // werden Nummern neu vergeben — die Projektsperre muss vor der Spaltensperre liegen.
    InOrder inOrder = inOrder(cards);
    inOrder.verify(cards).lockCardNumbers(2L);
    inOrder.verify(cards).lockColumnPositions(List.of(60L, 50L, 51L));
  }

  @Test
  void bulkTransfer_doesNotLockTheNumberSpace_withinTheSameProject() {
    // Given: Ziel- und Quellprojekt sind identisch — es wird keine Nummer neu vergeben
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, FIXED, CardType.CARD, null, null)));
    when(boardService.requireProjectId(20L)).thenReturn(PROJECT);
    when(boardService.requireColumn(60L, 20L)).thenReturn(new ColumnView(60L, "Backlog", 0, null));

    // When
    service.bulkTransfer(1L, List.of(100L), 20L, 60L);

    // Then: ein Sammel-Umzug im eigenen Projekt bremst die Karten-Anlage dort nicht aus.
    verify(cards, never()).lockCardNumbers(anyLong());
    verify(cards).lockColumnPositions(List.of(60L, 50L));
  }

  @Test
  void bulkTransfer_propagatesAndTransfersNoneWhenOneIsEpic() {
    // Given: erste Karte ein Epic -> Abbruch vor jeglichem Transfer (Rollback im echten Betrieb)
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, null, CardType.EPIC, null, "EP")));

    // When / Then
    assertThatThrownBy(() -> service.bulkTransfer(1L, List.of(100L, 101L), 20L, 60L))
        .isInstanceOf(InvalidDependencyException.class);
    verify(cards, never()).transfer(anyLong(), anyLong(), anyLong(), anyInt());
  }

  @Test
  void transfer_throwsBoardNotFound_whenTargetBoardUnknown() {
    // Given
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, null, CardType.CARD, null, null)));
    when(boardService.requireProjectId(20L)).thenThrow(new BoardNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.transfer(1L, 100L, 20L, 60L))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void transfer_throwsColumnNotFound_whenTargetColumnInOtherBoard() {
    // Given
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, null, CardType.CARD, null, null)));
    when(boardService.requireProjectId(20L)).thenReturn(2L);
    when(boardService.requireColumn(60L, 20L)).thenThrow(new ColumnNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.transfer(1L, 100L, 20L, 60L))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  // --- transfer auf dasselbe Board (Issue #1043) -------------------------

  /**
   * Karte 100 liegt auf {@link #BOARD} in Spalte 50; Zielspalte 60 desselben Boards. Zielboard ist
   * damit das eigene — der Umzug ist in Wahrheit ein Spaltenwechsel.
   */
  private void stubSelbesBoardScenario(String zielspalte, @Nullable Instant done) {
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, done, CardType.CARD, 9L, null)));
    when(boardService.requireColumn(60L, BOARD)).thenReturn(column(60L, zielspalte, 1));
  }

  @Test
  void transfer_aufDasEigeneBoard_verschiebtNurDieSpalteUndBehaeltNummerUndVorhaben() {
    // Das Zielboard ist das Board der Karte: kein Umzug (cards.transfer), sondern ein
    // Spaltenwechsel ans Ende der Zielspalte — Nummer und Vorhaben-Zuordnung bleiben.
    stubSelbesBoardScenario("Ready", null);

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    CardView view = service.transfer(1L, 100L, BOARD, 60L);

    verify(cards).move(100L, 60L, Integer.MAX_VALUE);
    verify(cards, never()).transfer(anyLong(), anyLong(), anyLong(), anyInt());
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().parentId()).isEqualTo(9L);
    assertThat(view.number()).isEqualTo(3);
  }

  @Test
  void transfer_aufDasEigeneBoard_schreibtSpaltenwechselUndVerlaufseintrag() {
    stubSelbesBoardScenario("Ready", null);

    service.transfer(9L, 100L, BOARD, 60L);

    verify(transitions).closeOpen(100L, FIXED);
    verify(transitions).open(100L, 60L, "Ready", FIXED);
    verify(activity)
        .add(
            100L,
            9L,
            CardActivityType.MOVED,
            "Verschoben nach Ready",
            FIXED,
            ActorContext.ActorStamp.unknown());
  }

  @Test
  void transfer_aufDasEigeneBoard_setztDenDoneZeitpunkt() {
    // Anders als beim Board-Wechsel (der movedToDoneAt immer leert) zählt hier der Eintritt in
    // eine Done-Spalte — sonst fehlte der Karte ihr Done-Zeitpunkt und Durchlauf-/
    // Implementierungszeit stimmten nicht mehr.
    stubSelbesBoardScenario("Done", null);

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.transfer(1L, 100L, BOARD, 60L);

    verify(cards).save(captor.capture());
    assertThat(captor.getValue().movedToDoneAt()).isEqualTo(FIXED);
  }

  @Test
  void transfer_aufDasEigeneBoard_loeschtDenDoneZeitpunktBeimVerlassen() {
    stubSelbesBoardScenario("Ready", FIXED.minusSeconds(10));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.transfer(1L, 100L, BOARD, 60L);

    verify(cards).save(captor.capture());
    assertThat(captor.getValue().movedToDoneAt()).isNull();
  }

  @Test
  void transfer_aufDasEigeneBoard_inDieSelbeSpalte_schreibtKeinenEintrag() {
    // Dieselbe Regel wie move: kein Eintrag bei reinem Reindex. Eine Auswahl über mehrere Spalten
    // soll nicht scheitern, nur weil eine Karte schon in der Zielspalte liegt.
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 60L, 3, false, null, CardType.CARD, null, null)));
    when(boardService.requireColumn(60L, BOARD)).thenReturn(column(60L, "Ready", 1));

    service.transfer(1L, 100L, BOARD, 60L);

    verify(transitions, never()).closeOpen(anyLong(), any());
    verify(transitions, never()).open(anyLong(), anyLong(), any(), any());
    verify(activity, never()).add(anyLong(), anyLong(), any(), any(), any(), any());
  }

  @Test
  void transfer_aufDasEigeneBoard_verlangtNurDasVerschieberecht() {
    stubSelbesBoardScenario("Ready", null);

    service.transfer(1L, 100L, BOARD, 60L);

    verify(permissions).require(1L, PROJECT, Permission.CARD_MOVE);
    verify(permissions, never()).requireOwner(anyLong(), anyLong());
  }

  @Test
  void transfer_aufDasEigeneBoard_lehntEpicsAb() {
    // Die Ablehnung steht vor der Weiche — ein Vorhaben wird auch auf dem eigenen Board nicht
    // positioniert, und die Meldung des Transfers bleibt erhalten.
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, null, CardType.EPIC, null, "EP")));

    assertThatThrownBy(() -> service.transfer(1L, 100L, BOARD, 60L))
        .isInstanceOf(InvalidDependencyException.class)
        .hasMessage("Epics können nicht verschoben werden");
    verify(cards, never()).move(anyLong(), anyLong(), anyInt());
  }

  @Test
  void bulkTransfer_aufDasEigeneBoard_verschiebtJedeKarteAnsEndeDerZielspalte() {
    when(cards.findById(100L))
        .thenReturn(Optional.of(card(100L, 50L, 3, false, null, CardType.CARD, 9L, null)));
    when(cards.findById(101L))
        .thenReturn(Optional.of(card(101L, 51L, 4, false, null, CardType.CARD, 9L, null)));
    when(boardService.requireColumn(60L, BOARD)).thenReturn(column(60L, "Ready", 1));

    List<CardView> result = service.bulkTransfer(1L, List.of(100L, 101L), BOARD, 60L);

    assertThat(result).extracting(CardView::id).containsExactly(100L, 101L);
    // Auswahlreihenfolge: jede Karte einzeln ans Ende — der Server sortiert nicht um.
    InOrder inOrder = inOrder(cards);
    inOrder.verify(cards).lockColumnPositions(List.of(60L, 50L, 51L));
    inOrder.verify(cards).move(100L, 60L, Integer.MAX_VALUE);
    inOrder.verify(cards).move(101L, 60L, Integer.MAX_VALUE);
    verify(cards, never()).lockCardNumbers(anyLong());
  }

  // --- Aktivitätsverlauf (card_activity) --------------------------------

  @Test
  void move_recordsMovedActivity_whenColumnChanges() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(boardService.requireColumn(21L, BOARD)).thenReturn(column(21L, "Done", 4));

    service.move(9L, 1L, 21L, 0);

    verify(activity)
        .add(
            1L,
            9L,
            CardActivityType.MOVED,
            "Verschoben nach Done",
            FIXED,
            ActorContext.ActorStamp.unknown());
  }

  // --- Zykluszeit-Tracking (card_column_transition) ---------------------

  @Test
  void move_closesOldAndOpensNewTransition_whenColumnChanges() {
    // Given
    Card before = card(1L, 20L, 1, false, null, CardType.CARD, null, null);
    when(cards.findById(1L)).thenReturn(Optional.of(before));
    when(boardService.requireColumn(21L, BOARD)).thenReturn(column(21L, "Done", 4));

    // When
    service.move(1L, 1L, 21L, 0);

    // Then — erst die verlassene Spalte schließen, dann die Zielspalte eröffnen.
    InOrder order = inOrder(transitions);
    order.verify(transitions).closeOpen(1L, FIXED);
    order.verify(transitions).open(1L, 21L, "Done", FIXED);
  }

  @Test
  void move_recordsNoTransition_whenColumnUnchanged() {
    // Given: Reindex innerhalb derselben Spalte (Ziel == aktuelle Spalte).
    Card before = card(1L, 20L, 1, false, null, CardType.CARD, null, null);
    when(cards.findById(1L)).thenReturn(Optional.of(before));
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    service.move(1L, 1L, 20L, 2);

    // Then — kein Spaltenwechsel, keine Transition.
    verify(transitions, never()).closeOpen(anyLong(), any());
    verify(transitions, never()).open(anyLong(), anyLong(), any(), any());
  }

  @Test
  void transfer_recordsColumnTransition() {
    // Given
    stubTransferScenario(null);

    // When
    service.transfer(1L, 100L, 20L, 60L);

    // Then — Umzug schließt die alte und eröffnet die Ziel-Spalte.
    InOrder order = inOrder(transitions);
    order.verify(transitions).closeOpen(100L, FIXED);
    order.verify(transitions).open(100L, 60L, "Backlog", FIXED);
  }

  // --- Live-Board-Events (#342): je board-relevanter Mutation ein BoardChangedEvent ------------

  private CardBoardActivityEvent onlyPublishedEvent() {
    ArgumentCaptor<CardBoardActivityEvent> captor =
        ArgumentCaptor.forClass(CardBoardActivityEvent.class);
    verify(events).publishEvent(captor.capture());
    return captor.getValue();
  }

  @Test
  void move_publishesMovedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(boardService.requireColumn(21L, BOARD)).thenReturn(column(21L, "Ready", 1));

    service.move(1L, 1L, 21L, 0);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.MOVED, 1L));
  }

  @Test
  void transfer_publishesMovedEventForBothBoards() {
    stubTransferScenario(null);

    service.transfer(1L, 100L, 20L, 60L);

    ArgumentCaptor<CardBoardActivityEvent> captor =
        ArgumentCaptor.forClass(CardBoardActivityEvent.class);
    verify(events, times(2)).publishEvent(captor.capture());
    assertThat(captor.getAllValues())
        .containsExactly(
            new CardBoardActivityEvent(BOARD, ActivityType.MOVED, 100L),
            new CardBoardActivityEvent(20L, ActivityType.MOVED, 100L));
  }

  // --- setStatus (Issue #1300, Plan #1294) ----------------------------

  private static Card paket(long id, String titel, CardType type, @Nullable CardStatus status) {
    return new Card(
        id, BOARD, 20L, 7, titel, null, 3, false, null, 1L, FIXED, FIXED, type, null, null, null,
        PROJECT, null, null, null, status);
  }

  private Card gespeichert() {
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    verify(cards).save(captor.capture());
    return captor.getValue();
  }

  @Test
  void setStatus_laesstSpalteUndPositionUnveraendert() {
    when(cards.findById(5L))
        .thenReturn(Optional.of(paket(5L, "Paket", CardType.CARD, CardStatus.BACKLOG)));

    service.setStatus(1L, 5L, "IN_PROGRESS");

    Card nachher = gespeichert();
    assertThat(nachher.status()).isEqualTo(CardStatus.IN_PROGRESS);
    assertThat(nachher.columnId()).isEqualTo(20L);
    assertThat(nachher.positionInColumn()).isEqualTo(3);
    verify(cards, never()).move(anyLong(), anyLong(), anyInt());
  }

  @Test
  void setStatus_verlangtCardMove_undSchreibtOhneRechtNichts() {
    when(cards.findById(5L))
        .thenReturn(Optional.of(paket(5L, "Paket", CardType.CARD, CardStatus.BACKLOG)));
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .require(1L, PROJECT, Permission.CARD_MOVE);

    assertThatThrownBy(() -> service.setStatus(1L, 5L, "READY"))
        .isInstanceOf(ProjectAccessDeniedException.class);

    verify(cards, never()).save(any(Card.class));
    verify(transitions, never()).closeOpen(anyLong(), any(Instant.class));
    verify(events, never()).publishEvent(any(Object.class));
  }

  @Test
  void setStatus_weistUnbekanntenWertAb() {
    when(cards.findById(5L))
        .thenReturn(Optional.of(paket(5L, "Paket", CardType.CARD, CardStatus.BACKLOG)));

    assertThatThrownBy(() -> service.setStatus(1L, 5L, "FERTIG"))
        .isInstanceOf(InvalidStatusException.class);
    // Nur der Konstantenname gilt (E24) — ein Anzeigename oder Kleinschreibung ist kein Status.
    assertThatThrownBy(() -> service.setStatus(1L, 5L, "In review"))
        .isInstanceOf(InvalidStatusException.class);
    assertThatThrownBy(() -> service.setStatus(1L, 5L, "done"))
        .isInstanceOf(InvalidStatusException.class);

    verify(cards, never()).save(any(Card.class));
  }

  @Test
  void setStatus_weistVorhabenAb() {
    when(cards.findById(5L)).thenReturn(Optional.of(paket(5L, "Vorhaben", CardType.EPIC, null)));

    assertThatThrownBy(() -> service.setStatus(1L, 5L, "READY"))
        .isInstanceOf(InvalidStatusException.class);

    verify(cards, never()).save(any(Card.class));
  }

  @Test
  void setStatus_weistDokumentartAb() {
    when(cards.findById(5L))
        .thenReturn(Optional.of(paket(5L, "[Fachlich] Anforderung", CardType.CARD, null)));

    assertThatThrownBy(() -> service.setStatus(1L, 5L, "READY"))
        .isInstanceOf(InvalidStatusException.class);

    verify(cards, never()).save(any(Card.class));
    verify(activity, never())
        .add(anyLong(), anyLong(), any(), any(), any(), any(ActorContext.ActorStamp.class));
  }

  @Test
  void setStatus_schreibtStatusChangedMitKanonischemProzessnamen() {
    when(cards.findById(5L))
        .thenReturn(Optional.of(paket(5L, "Paket", CardType.CARD, CardStatus.IN_PROGRESS)));

    service.setStatus(1L, 5L, "IN_REVIEW");

    verify(activity)
        .add(
            5L,
            1L,
            CardActivityType.STATUS_CHANGED,
            "Status auf In review",
            FIXED,
            ActorContext.ActorStamp.unknown());
  }

  @Test
  void setStatus_veroeffentlichtUpdated() {
    when(cards.findById(5L))
        .thenReturn(Optional.of(paket(5L, "Paket", CardType.CARD, CardStatus.BACKLOG)));

    service.setStatus(1L, 5L, "READY");

    verify(events).publishEvent(new CardBoardActivityEvent(BOARD, ActivityType.UPDATED, 5L));
  }

  @Test
  void setStatus_fuehrtDenAufenthaltMitDemProzessnamenInDerEigenenSpalte() {
    when(cards.findById(5L))
        .thenReturn(Optional.of(paket(5L, "Paket", CardType.CARD, CardStatus.BACKLOG)));

    service.setStatus(1L, 5L, "IN_PROGRESS");

    InOrder reihenfolge = inOrder(transitions);
    reihenfolge.verify(transitions).closeOpen(5L, FIXED);
    reihenfolge.verify(transitions).open(5L, 20L, "In progress", FIXED);
  }

  @Test
  void setStatus_setztDoneStempelBeimWechselAufDone() {
    when(cards.findById(5L))
        .thenReturn(Optional.of(paket(5L, "Paket", CardType.CARD, CardStatus.IN_REVIEW)));

    service.setStatus(1L, 5L, "DONE");

    assertThat(gespeichert().movedToDoneAt()).isEqualTo(FIXED);
  }

  @Test
  void setStatus_raeumtDoneStempelBeimWechselWegVonDone() {
    Card erledigt =
        paket(5L, "Paket", CardType.CARD, CardStatus.DONE)
            .withMovedToDoneAt(Instant.parse("2025-12-01T00:00:00Z"));
    when(cards.findById(5L)).thenReturn(Optional.of(erledigt));

    service.setStatus(1L, 5L, "IN_REVIEW");

    Card nachher = gespeichert();
    assertThat(nachher.movedToDoneAt()).isNull();
    assertThat(nachher.status()).isEqualTo(CardStatus.IN_REVIEW);
  }

  @Test
  void setStatus_aufDenBisherigenStatus_hinterlaesstKeineSpur() {
    when(cards.findById(5L))
        .thenReturn(Optional.of(paket(5L, "Paket", CardType.CARD, CardStatus.READY)));

    service.setStatus(1L, 5L, "READY");

    verify(permissions).require(1L, PROJECT, Permission.CARD_MOVE);
    verify(cards, never()).save(any(Card.class));
    verify(transitions, never()).closeOpen(anyLong(), any(Instant.class));
    verify(activity, never())
        .add(anyLong(), anyLong(), any(), any(), any(), any(ActorContext.ActorStamp.class));
    verify(events, never()).publishEvent(any(Object.class));
  }

  @Test
  void setStatus_unbekannteKarte_wirftNotFound() {
    when(cards.findById(5L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.setStatus(1L, 5L, "READY"))
        .isInstanceOf(CardNotFoundException.class);
  }
}
