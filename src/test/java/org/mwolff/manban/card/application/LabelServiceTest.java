package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.card.domain.Label;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.context.ApplicationEventPublisher;

/** Verhaltenstests der Label-Verwaltung (Ports gemockt). */
// PMD.TooManyMethods: methodenreiche Testsuite — viele kleine @Test-Methoden je Erfolgs- und
// Fehlerpfad sind hier gewollt, kein Refactoring-Signal.
@SuppressWarnings("PMD.TooManyMethods")
class LabelServiceTest {

  private static final long BOARD = 10L;
  private static final long OTHER_BOARD = 11L;
  private static final long PROJECT = 1L;
  private static final long CARD_ID = 30L;
  private static final long USER = 5L;
  private static final Instant FIXED = Instant.parse("2026-01-01T00:00:00Z");

  private LabelRepository labels;
  private CardLabelRepository cardLabels;
  private CardRepository cards;
  private BoardService boardService;
  private PermissionChecker permissions;
  private ApplicationEventPublisher events;
  private LabelService service;

  @BeforeEach
  void setUp() {
    labels = mock(LabelRepository.class);
    cardLabels = mock(CardLabelRepository.class);
    cards = mock(CardRepository.class);
    boardService = mock(BoardService.class);
    permissions = mock(PermissionChecker.class);
    events = mock(ApplicationEventPublisher.class);
    service = new LabelService(labels, cardLabels, cards, boardService, permissions, events);
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(labels.save(any(Label.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  /** Karte des Boards {@link #BOARD} im Projekt {@link #PROJECT}. */
  private static Card card(CardType type) {
    return new Card(
        CARD_ID, BOARD, 100L, 7, "Titel", null, 0, false, false, null, 1L, FIXED, FIXED, type, null,
        null, null, PROJECT, null, null);
  }

  private void givenCard(CardType type) {
    when(cards.findById(CARD_ID)).thenReturn(Optional.of(card(type)));
  }

  private void givenBoardLabel(long labelId, String name) {
    when(labels.findByBoardId(BOARD)).thenReturn(List.of(new Label(labelId, BOARD, name, "#f00")));
  }

  @Test
  void list_requiresMembershipAndReturnsLabels() {
    when(labels.findByBoardId(BOARD)).thenReturn(List.of(new Label(1L, BOARD, "Bug", "#f00")));

    List<Label> result = service.list(5L, BOARD);

    verify(permissions).requireMembership(5L, PROJECT);
    assertThat(result).extracting(Label::name).containsExactly("Bug");
  }

  @Test
  void list_throwsBoardNotFound_whenBoardUnknown() {
    when(boardService.requireProjectId(BOARD)).thenThrow(new BoardNotFoundException());

    assertThatThrownBy(() -> service.list(5L, BOARD)).isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void create_trimsNameAndPersists() {
    when(labels.existsByBoardIdAndName(BOARD, "Bug")).thenReturn(false);

    ArgumentCaptor<Label> captor = ArgumentCaptor.forClass(Label.class);
    Label created = service.create(5L, BOARD, "  Bug  ", "#f00");

    verify(permissions).require(5L, PROJECT, Permission.BOARD_UPDATE);
    verify(labels).save(captor.capture());
    assertThat(captor.getValue().name()).isEqualTo("Bug");
    assertThat(captor.getValue().color()).isEqualTo("#f00");
    assertThat(created.name()).isEqualTo("Bug");
  }

  @Test
  void create_rejectsBlankName() {
    assertThatThrownBy(() -> service.create(5L, BOARD, "   ", "#f00"))
        .isInstanceOf(InvalidLabelException.class);
    verify(labels, never()).save(any());
  }

  @Test
  void create_rejectsDuplicateName() {
    when(labels.existsByBoardIdAndName(BOARD, "Bug")).thenReturn(true);

    assertThatThrownBy(() -> service.create(5L, BOARD, "Bug", "#f00"))
        .isInstanceOf(InvalidLabelException.class);
    verify(labels, never()).save(any());
  }

  @Test
  void update_changesNameAndColor() {
    when(labels.findById(1L)).thenReturn(Optional.of(new Label(1L, BOARD, "Bug", "#f00")));

    ArgumentCaptor<Label> captor = ArgumentCaptor.forClass(Label.class);
    Label result = service.update(5L, 1L, "Defekt", "#00f");

    verify(permissions).require(5L, PROJECT, Permission.BOARD_UPDATE);
    verify(labels).save(captor.capture());
    assertThat(captor.getValue().name()).isEqualTo("Defekt");
    assertThat(captor.getValue().color()).isEqualTo("#00f");
    assertThat(result.name()).isEqualTo("Defekt");
  }

  @Test
  void update_allowsSameNameUnchanged() {
    when(labels.findById(1L)).thenReturn(Optional.of(new Label(1L, BOARD, "Bug", "#f00")));
    // Der eigene Name existiert (per Definition) — der Gleichheits-Kurzschluss muss den
    // Duplikat-Check überspringen, sonst würde das Umfärben fälschlich scheitern.
    when(labels.existsByBoardIdAndName(BOARD, "Bug")).thenReturn(true);

    service.update(5L, 1L, "Bug", "#0f0");

    verify(labels).save(any(Label.class));
  }

  @Test
  void update_rejectsRenameToExistingName() {
    when(labels.findById(1L)).thenReturn(Optional.of(new Label(1L, BOARD, "Bug", "#f00")));
    when(labels.existsByBoardIdAndName(BOARD, "Ux")).thenReturn(true);

    assertThatThrownBy(() -> service.update(5L, 1L, "Ux", "#0f0"))
        .isInstanceOf(InvalidLabelException.class);
    verify(labels, never()).save(any());
  }

  @Test
  void update_throwsLabelNotFound_whenUnknown() {
    when(labels.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(5L, 1L, "X", "#0f0"))
        .isInstanceOf(LabelNotFoundException.class);
  }

  @Test
  void delete_removesLabel() {
    when(labels.findById(1L)).thenReturn(Optional.of(new Label(1L, BOARD, "Bug", "#f00")));

    service.delete(5L, 1L);

    verify(permissions).require(5L, PROJECT, Permission.BOARD_UPDATE);
    verify(labels).deleteById(1L);
  }

  @Test
  void delete_throwsLabelNotFound_whenUnknown() {
    when(labels.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(5L, 1L)).isInstanceOf(LabelNotFoundException.class);
    verify(labels, never()).deleteById(anyLong());
  }

  // --- namesByCard: Batch-Auflösung der Label-Namen je Karte (#458) ---------------------------

  @Test
  void namesByCard_mapsAssignedLabelIdsToNames() {
    when(labels.findByBoardId(BOARD))
        .thenReturn(
            List.of(new Label(7L, BOARD, "Bug", "#f00"), new Label(8L, BOARD, "Ux", "#0f0")));
    when(cardLabels.findByCardIds(List.of(1L))).thenReturn(Map.of(1L, List.of(7L, 8L)));

    Map<Long, List<String>> result = service.namesByCard(BOARD, List.of(1L));

    assertThat(result).containsEntry(1L, List.of("Bug", "Ux"));
  }

  @Test
  void namesByCard_returnsEmptyList_forCardWithoutLabels() {
    // Karten ohne Zuordnung fehlen in der Batch-Antwort — sie müssen dennoch als Eintrag mit
    // leerer Liste erscheinen, sonst müsste jeder Aufrufer den Null-Fall behandeln.
    when(labels.findByBoardId(BOARD)).thenReturn(List.of(new Label(7L, BOARD, "Bug", "#f00")));
    when(cardLabels.findByCardIds(List.of(1L))).thenReturn(Map.of());

    Map<Long, List<String>> result = service.namesByCard(BOARD, List.of(1L));

    assertThat(result).containsEntry(1L, List.of());
  }

  @Test
  void namesByCard_ordersNamesByBoardDefinition_notByAssignment() {
    // Die Zuordnung nennt (8, 7), das Board definiert (7 "Bug", 8 "Ux") — die Board-Reihenfolge
    // gewinnt, damit die Ausgabe unabhängig von der Zuordnungsreihenfolge stabil bleibt.
    when(labels.findByBoardId(BOARD))
        .thenReturn(
            List.of(new Label(7L, BOARD, "Bug", "#f00"), new Label(8L, BOARD, "Ux", "#0f0")));
    when(cardLabels.findByCardIds(List.of(1L))).thenReturn(Map.of(1L, List.of(8L, 7L)));

    Map<Long, List<String>> result = service.namesByCard(BOARD, List.of(1L));

    assertThat(result).containsEntry(1L, List.of("Bug", "Ux"));
  }

  @Test
  void namesByCard_ignoresLabelsOfOtherCards() {
    // Zwei Karten, jede mit eigener Zuordnung: die Namen dürfen nicht über Karten hinweg verlaufen.
    when(labels.findByBoardId(BOARD))
        .thenReturn(
            List.of(new Label(7L, BOARD, "Bug", "#f00"), new Label(8L, BOARD, "Ux", "#0f0")));
    when(cardLabels.findByCardIds(List.of(1L, 2L)))
        .thenReturn(Map.of(1L, List.of(7L), 2L, List.of(8L)));

    Map<Long, List<String>> result = service.namesByCard(BOARD, List.of(1L, 2L));

    assertThat(result).containsEntry(1L, List.of("Bug")).containsEntry(2L, List.of("Ux"));
  }

  @Test
  void namesByCard_returnsEmptyMap_forNoCards() {
    when(labels.findByBoardId(BOARD)).thenReturn(List.of(new Label(7L, BOARD, "Bug", "#f00")));
    when(cardLabels.findByCardIds(List.of())).thenReturn(Map.of());

    assertThat(service.namesByCard(BOARD, List.of())).isEmpty();
  }

  // --- addToCard / removeFromCard: atomare Einzel-Zuordnung (#574) ----------------------------

  @Test
  void addToCard_assignsResolvedLabelAndPublishesBoardEvent() {
    givenCard(CardType.CARD);
    givenBoardLabel(7L, "kit:nightrun");
    when(cardLabels.addLabel(CARD_ID, 7L)).thenReturn(true);

    service.addToCard(USER, CARD_ID, "kit:nightrun");

    verify(permissions).require(USER, PROJECT, Permission.TICKET_UPDATE);
    verify(cardLabels).addLabel(CARD_ID, 7L);
    verify(events).publishEvent(new CardBoardActivityEvent(BOARD, ActivityType.UPDATED, CARD_ID));
  }

  @Test
  void addToCard_neverReplacesTheWholeAssignment() {
    // Der Kern der Aufgabe: replaceLabels würde fremde Labels stillschweigend löschen, sobald
    // parallel am Board gearbeitet wird.
    givenCard(CardType.CARD);
    givenBoardLabel(7L, "kit:nightrun");
    when(cardLabels.addLabel(CARD_ID, 7L)).thenReturn(true);

    service.addToCard(USER, CARD_ID, "kit:nightrun");

    verify(cardLabels, never()).replaceLabels(anyLong(), any());
  }

  @Test
  void addToCard_publishesNoEvent_whenLabelWasAlreadyAssigned() {
    // Idempotenz nach außen (Erfolg), aber ohne Ereignis: ein wiederholter Nachtlauf darf keine
    // Änderung melden, die es nicht gab.
    givenCard(CardType.CARD);
    givenBoardLabel(7L, "kit:nightrun");
    when(cardLabels.addLabel(CARD_ID, 7L)).thenReturn(false);

    service.addToCard(USER, CARD_ID, "kit:nightrun");

    verify(events, never()).publishEvent(any(CardBoardActivityEvent.class));
  }

  @Test
  void addToCard_resolvesTheNameOnTheCardsBoard_notOnAnotherBoard() {
    // Labelnamen sind nur boardweit eindeutig: dasselbe "Bug" existiert auf zwei Boards.
    givenCard(CardType.CARD);
    when(labels.findByBoardId(BOARD)).thenReturn(List.of(new Label(7L, BOARD, "Bug", "#f00")));
    when(labels.findByBoardId(OTHER_BOARD))
        .thenReturn(List.of(new Label(99L, OTHER_BOARD, "Bug", "#0f0")));
    when(cardLabels.addLabel(CARD_ID, 7L)).thenReturn(true);

    service.addToCard(USER, CARD_ID, "Bug");

    verify(cardLabels).addLabel(CARD_ID, 7L);
    verify(cardLabels, never()).addLabel(CARD_ID, 99L);
  }

  @Test
  void addToCard_trimsTheName() {
    givenCard(CardType.CARD);
    givenBoardLabel(7L, "Bug");
    when(cardLabels.addLabel(CARD_ID, 7L)).thenReturn(true);

    service.addToCard(USER, CARD_ID, "  Bug  ");

    verify(cardLabels).addLabel(CARD_ID, 7L);
  }

  @Test
  void addToCard_comparesTheNameCaseSensitively() {
    givenCard(CardType.CARD);
    givenBoardLabel(7L, "Bug");

    assertThatThrownBy(() -> service.addToCard(USER, CARD_ID, "bug"))
        .isInstanceOf(LabelNotFoundException.class);
    verify(cardLabels, never()).addLabel(anyLong(), anyLong());
  }

  @Test
  void addToCard_rejectsBlankName() {
    givenCard(CardType.CARD);

    assertThatThrownBy(() -> service.addToCard(USER, CARD_ID, "   "))
        .isInstanceOf(InvalidLabelException.class);
    verify(cardLabels, never()).addLabel(anyLong(), anyLong());
  }

  @Test
  void addToCard_throwsLabelNotFound_andCreatesNothing_whenNameUnknown() {
    // Ein Tippfehler im Nachtlauf darf kein Label anlegen — sonst entsteht unbemerkt Label-Müll.
    givenCard(CardType.CARD);
    givenBoardLabel(7L, "Bug");

    assertThatThrownBy(() -> service.addToCard(USER, CARD_ID, "Unbekannt"))
        .isInstanceOf(LabelNotFoundException.class);
    verify(labels, never()).save(any());
    verify(cardLabels, never()).addLabel(anyLong(), anyLong());
  }

  @Test
  void addToCard_throwsCardNotFound_whenCardUnknown() {
    when(cards.findById(CARD_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.addToCard(USER, CARD_ID, "Bug"))
        .isInstanceOf(CardNotFoundException.class);
    verify(cardLabels, never()).addLabel(anyLong(), anyLong());
  }

  @Test
  void addToCard_rejectsEpics() {
    givenCard(CardType.EPIC);

    assertThatThrownBy(() -> service.addToCard(USER, CARD_ID, "Bug"))
        .isInstanceOf(InvalidDependencyException.class)
        .hasMessageContaining("Nur Karten haben Labels");
    verify(cardLabels, never()).addLabel(anyLong(), anyLong());
  }

  @Test
  void addToCard_requiresTicketUpdate() {
    givenCard(CardType.CARD);
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .require(USER, PROJECT, Permission.TICKET_UPDATE);

    assertThatThrownBy(() -> service.addToCard(USER, CARD_ID, "Bug"))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(cardLabels, never()).addLabel(anyLong(), anyLong());
  }

  @Test
  void removeFromCard_removesResolvedLabelAndPublishesBoardEvent() {
    givenCard(CardType.CARD);
    givenBoardLabel(7L, "kit:nightrun");
    when(cardLabels.removeLabel(CARD_ID, 7L)).thenReturn(true);

    service.removeFromCard(USER, CARD_ID, "kit:nightrun");

    verify(permissions).require(USER, PROJECT, Permission.TICKET_UPDATE);
    verify(cardLabels).removeLabel(CARD_ID, 7L);
    verify(events).publishEvent(new CardBoardActivityEvent(BOARD, ActivityType.UPDATED, CARD_ID));
  }

  @Test
  void removeFromCard_neverReplacesTheWholeAssignment() {
    givenCard(CardType.CARD);
    givenBoardLabel(7L, "kit:nightrun");
    when(cardLabels.removeLabel(CARD_ID, 7L)).thenReturn(true);

    service.removeFromCard(USER, CARD_ID, "kit:nightrun");

    verify(cardLabels, never()).replaceLabels(anyLong(), any());
  }

  @Test
  void removeFromCard_publishesNoEvent_whenLabelWasNotAssigned() {
    givenCard(CardType.CARD);
    givenBoardLabel(7L, "kit:nightrun");
    when(cardLabels.removeLabel(CARD_ID, 7L)).thenReturn(false);

    service.removeFromCard(USER, CARD_ID, "kit:nightrun");

    verify(events, never()).publishEvent(any(CardBoardActivityEvent.class));
  }

  @Test
  void removeFromCard_resolvesTheNameOnTheCardsBoard_notOnAnotherBoard() {
    givenCard(CardType.CARD);
    when(labels.findByBoardId(BOARD)).thenReturn(List.of(new Label(7L, BOARD, "Bug", "#f00")));
    when(labels.findByBoardId(OTHER_BOARD))
        .thenReturn(List.of(new Label(99L, OTHER_BOARD, "Bug", "#0f0")));
    when(cardLabels.removeLabel(CARD_ID, 7L)).thenReturn(true);

    service.removeFromCard(USER, CARD_ID, "Bug");

    verify(cardLabels).removeLabel(CARD_ID, 7L);
    verify(cardLabels, never()).removeLabel(CARD_ID, 99L);
  }

  @Test
  void removeFromCard_trimsTheName() {
    givenCard(CardType.CARD);
    givenBoardLabel(7L, "Bug");
    when(cardLabels.removeLabel(CARD_ID, 7L)).thenReturn(true);

    service.removeFromCard(USER, CARD_ID, "  Bug  ");

    verify(cardLabels).removeLabel(CARD_ID, 7L);
  }

  @Test
  void removeFromCard_rejectsBlankName() {
    givenCard(CardType.CARD);

    assertThatThrownBy(() -> service.removeFromCard(USER, CARD_ID, "   "))
        .isInstanceOf(InvalidLabelException.class);
    verify(cardLabels, never()).removeLabel(anyLong(), anyLong());
  }

  @Test
  void removeFromCard_throwsLabelNotFound_andCreatesNothing_whenNameUnknown() {
    givenCard(CardType.CARD);
    givenBoardLabel(7L, "Bug");

    assertThatThrownBy(() -> service.removeFromCard(USER, CARD_ID, "Unbekannt"))
        .isInstanceOf(LabelNotFoundException.class);
    verify(labels, never()).save(any());
    verify(cardLabels, never()).removeLabel(anyLong(), anyLong());
  }

  @Test
  void removeFromCard_throwsCardNotFound_whenCardUnknown() {
    when(cards.findById(CARD_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.removeFromCard(USER, CARD_ID, "Bug"))
        .isInstanceOf(CardNotFoundException.class);
    verify(cardLabels, never()).removeLabel(anyLong(), anyLong());
  }

  @Test
  void removeFromCard_rejectsEpics() {
    givenCard(CardType.EPIC);

    assertThatThrownBy(() -> service.removeFromCard(USER, CARD_ID, "Bug"))
        .isInstanceOf(InvalidDependencyException.class)
        .hasMessageContaining("Nur Karten haben Labels");
    verify(cardLabels, never()).removeLabel(anyLong(), anyLong());
  }

  @Test
  void removeFromCard_requiresTicketUpdate() {
    givenCard(CardType.CARD);
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .require(USER, PROJECT, Permission.TICKET_UPDATE);

    assertThatThrownBy(() -> service.removeFromCard(USER, CARD_ID, "Bug"))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(cardLabels, never()).removeLabel(anyLong(), anyLong());
  }
}
