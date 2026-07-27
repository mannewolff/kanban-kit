package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.domain.Label;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;

/** Verhaltenstests der Label-Verwaltung (Ports gemockt). */
class LabelServiceTest {

  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;

  private LabelRepository labels;
  private CardLabelRepository cardLabels;
  private BoardService boardService;
  private PermissionChecker permissions;
  private LabelService service;

  @BeforeEach
  void setUp() {
    labels = mock(LabelRepository.class);
    cardLabels = mock(CardLabelRepository.class);
    boardService = mock(BoardService.class);
    permissions = mock(PermissionChecker.class);
    service = new LabelService(labels, cardLabels, boardService, permissions);
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(labels.save(any(Label.class))).thenAnswer(inv -> inv.getArgument(0));
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
}
