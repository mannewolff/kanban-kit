package org.mwolff.manban.board.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Verhaltenstests des Board-Archiv-Lebenszyklus (archivieren/wiederherstellen/endgültig löschen).
 */
class BoardArchiveServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private BoardRepository boards;
  private PermissionChecker permissions;
  private ApplicationEventPublisher events;
  private BoardService service;

  private static Board board() {
    return new Board(10L, 1L, "Board", FIXED);
  }

  @BeforeEach
  void setUp() {
    boards = mock(BoardRepository.class);
    BoardColumnRepository columns = mock(BoardColumnRepository.class);
    ColumnCardCounter cardCounter = mock(ColumnCardCounter.class);
    permissions = mock(PermissionChecker.class);
    events = mock(ApplicationEventPublisher.class);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    service = new BoardService(boards, columns, cardCounter, permissions, events, clock);
    when(boards.save(any(Board.class))).thenAnswer(inv -> inv.getArgument(0));
    when(columns.findByBoardId(10L)).thenReturn(List.of());
  }

  @Test
  void deleteBoard_archivesWithClockInstant_insteadOfDeleting() {
    // Given
    when(boards.findById(10L)).thenReturn(Optional.of(board()));

    // When
    ArgumentCaptor<Board> captor = ArgumentCaptor.forClass(Board.class);
    service.deleteBoard(1L, 10L);

    // Then
    verify(boards).save(captor.capture());
    assertThat(captor.getValue().archivedAt()).isEqualTo(FIXED);
    verify(boards, never()).deleteById(anyLong());
  }

  @Test
  void deleteBoard_requiresBoardDeletePermission() {
    // Given
    when(boards.findById(10L)).thenReturn(Optional.of(board()));

    // When
    service.deleteBoard(1L, 10L);

    // Then
    verify(permissions).require(1L, 1L, Permission.BOARD_DELETE);
  }

  @Test
  void listArchivedBoards_mapsArchivedBoardsToViews() {
    // Given
    when(boards.findArchivedByProjectId(1L)).thenReturn(List.of(board().archivedAt(FIXED)));

    // When
    List<BoardService.BoardView> views = service.listArchivedBoards(1L, 1L);

    // Then
    assertThat(views).singleElement().extracting(BoardService.BoardView::id).isEqualTo(10L);
  }

  @Test
  void restoreBoard_clearsArchivedAtAndReturnsView() {
    // Given
    when(boards.findByIdIncludingArchived(10L)).thenReturn(Optional.of(board().archivedAt(FIXED)));

    // When
    ArgumentCaptor<Board> captor = ArgumentCaptor.forClass(Board.class);
    BoardService.BoardView view = service.restoreBoard(1L, 10L);

    // Then
    verify(boards).save(captor.capture());
    assertThat(captor.getValue().archivedAt()).isNull();
    assertThat(view.name()).isEqualTo("Board");
  }

  @Test
  void restoreBoard_requiresBoardDeletePermission() {
    // Given
    when(boards.findByIdIncludingArchived(10L)).thenReturn(Optional.of(board().archivedAt(FIXED)));

    // When
    service.restoreBoard(1L, 10L);

    // Then
    verify(permissions).require(1L, 1L, Permission.BOARD_DELETE);
  }

  @Test
  void restoreBoard_throwsBoardNotFound_whenUnknown() {
    // Given
    when(boards.findByIdIncludingArchived(10L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.restoreBoard(1L, 10L))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void purgeBoard_deletesById_whenArchived() {
    // Given
    when(boards.findByIdIncludingArchived(10L)).thenReturn(Optional.of(board().archivedAt(FIXED)));

    // When
    service.purgeBoard(1L, 10L);

    // Then
    verify(boards).deleteById(10L);
  }

  @Test
  void purgeBoard_publishesBoardPurgedBeforeDeleting() {
    // Given
    when(boards.findByIdIncludingArchived(10L)).thenReturn(Optional.of(board().archivedAt(FIXED)));

    // When
    service.purgeBoard(1L, 10L);

    // Then — Reihenfolge ist die Zusage aus #503: Das card-Modul (und dahinter die Anhänge)
    // müssen die Metadaten noch sehen, bevor die Cascade sie entfernt.
    InOrder inOrder = inOrder(events, boards);
    inOrder.verify(events).publishEvent(new BoardPurgedEvent(10L));
    inOrder.verify(boards).deleteById(10L);
  }

  @Test
  void purgeBoard_requiresBoardDeletePermission() {
    // Given
    when(boards.findByIdIncludingArchived(10L)).thenReturn(Optional.of(board().archivedAt(FIXED)));

    // When
    service.purgeBoard(1L, 10L);

    // Then
    verify(permissions).require(1L, 1L, Permission.BOARD_DELETE);
  }

  @Test
  void purgeBoard_throwsNotArchived_whenBoardStillActive() {
    // Given
    when(boards.findByIdIncludingArchived(10L)).thenReturn(Optional.of(board()));

    // When / Then
    assertThatThrownBy(() -> service.purgeBoard(1L, 10L))
        .isInstanceOf(BoardNotArchivedException.class);
    verify(boards, never()).deleteById(anyLong());
    verifyNoInteractions(events);
  }

  @Test
  void purgeBoard_throwsBoardNotFound_whenUnknown() {
    // Given
    when(boards.findByIdIncludingArchived(10L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.purgeBoard(1L, 10L))
        .isInstanceOf(BoardNotFoundException.class);
  }
}
