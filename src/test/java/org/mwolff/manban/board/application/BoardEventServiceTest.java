package org.mwolff.manban.board.application;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectNotFoundException;

/** Autorisierung des Live-Board-Abos (Mockito an BoardRepository + PermissionChecker). */
class BoardEventServiceTest {

  private static final long USER = 7L;
  private static final long BOARD_ID = 3L;
  private static final long PROJECT_ID = 5L;

  private BoardRepository boards;
  private PermissionChecker permissions;
  private BoardEventService service;

  @BeforeEach
  void setUp() {
    boards = mock(BoardRepository.class);
    permissions = mock(PermissionChecker.class);
    service = new BoardEventService(boards, permissions);
  }

  @Test
  void requireSubscribable_passesForMember() {
    when(boards.findById(BOARD_ID))
        .thenReturn(Optional.of(new Board(BOARD_ID, PROJECT_ID, "B", Instant.EPOCH)));

    assertThatNoException().isThrownBy(() -> service.requireSubscribable(USER, BOARD_ID));

    verify(permissions).requireMembership(USER, PROJECT_ID);
  }

  @Test
  void requireSubscribable_rejectsUnknownBoard_withoutMembershipCheck() {
    when(boards.findById(BOARD_ID)).thenReturn(Optional.empty());

    assertThatExceptionOfType(BoardNotFoundException.class)
        .isThrownBy(() -> service.requireSubscribable(USER, BOARD_ID));

    verify(permissions, never())
        .requireMembership(ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong());
  }

  @Test
  void requireSubscribable_propagatesMembershipRejection() {
    when(boards.findById(BOARD_ID))
        .thenReturn(Optional.of(new Board(BOARD_ID, PROJECT_ID, "B", Instant.EPOCH)));
    when(permissions.requireMembership(USER, PROJECT_ID)).thenThrow(new ProjectNotFoundException());

    assertThatExceptionOfType(ProjectNotFoundException.class)
        .isThrownBy(() -> service.requireSubscribable(USER, BOARD_ID));
  }
}
