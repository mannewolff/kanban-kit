package org.mwolff.manban.board.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.project.application.ProjectCreatedEvent;

/** Verhaltenstest des Event-Listeners, der zu jedem neuen Projekt das Default-Board anlegt. */
class DefaultBoardCreatorTest {

  @Test
  void onProjectCreated_createsDefaultBoardForOwner() {
    // Given
    BoardService boardService = mock(BoardService.class);
    DefaultBoardCreator creator = new DefaultBoardCreator(boardService);

    // When
    creator.onProjectCreated(new ProjectCreatedEvent(9L, 2L));

    // Then
    verify(boardService).createBoard(2L, 9L, "default");
  }
}
