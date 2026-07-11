package org.mwolff.manban.board.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.project.application.PermissionChecker;

/** Zeit-Test: der Anlege-Zeitstempel eines Boards stammt aus der injizierten Clock. */
class BoardServiceTest {

    private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void createBoard_setsCreatedAtFromInjectedClock() {
        // Given
        BoardRepository boards = mock(BoardRepository.class);
        BoardColumnRepository columns = mock(BoardColumnRepository.class);
        ColumnCardCounter cardCounter = mock(ColumnCardCounter.class);
        PermissionChecker permissions = mock(PermissionChecker.class);
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
        when(boards.save(any(Board.class))).thenAnswer(inv -> {
            Board b = inv.getArgument(0);
            return new Board(1L, b.projectId(), b.name(), b.createdAt());
        });
        BoardService service = new BoardService(boards, columns, cardCounter, permissions, clock);

        // When
        service.createBoard(1L, 2L, "Board");

        // Then
        ArgumentCaptor<Board> captor = ArgumentCaptor.forClass(Board.class);
        verify(boards).save(captor.capture());
        assertThat(captor.getValue().createdAt()).isEqualTo(FIXED);
    }
}
