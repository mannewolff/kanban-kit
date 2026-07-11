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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.board.application.BoardColumnRepository;
import org.mwolff.manban.board.application.BoardRepository;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.board.domain.BoardColumn;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.project.application.PermissionChecker;

/** Zeit-Test: der Anlege-Zeitstempel einer Karte stammt aus der injizierten Clock. */
class CardServiceTest {

    private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void create_setsCreatedAtFromInjectedClock() {
        // Given
        CardRepository cards = mock(CardRepository.class);
        CardDependencyRepository dependencies = mock(CardDependencyRepository.class);
        BoardRepository boards = mock(BoardRepository.class);
        BoardColumnRepository columns = mock(BoardColumnRepository.class);
        PermissionChecker permissions = mock(PermissionChecker.class);
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
        when(boards.findById(10L)).thenReturn(Optional.of(new Board(10L, 1L, "B", FIXED)));
        when(columns.findById(20L)).thenReturn(Optional.of(new BoardColumn(20L, 10L, "Backlog", 0, null)));
        when(cards.maxNumberInBoard(10L)).thenReturn(0);
        when(cards.maxActivePositionInColumn(20L)).thenReturn(-1);
        when(cards.save(any(Card.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        CardService service = new CardService(cards, dependencies, boards, columns, permissions, clock);

        // When
        service.create(1L, 10L, 20L, "Titel", null, null, null);

        // Then
        ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
        verify(cards).save(captor.capture());
        assertThat(captor.getValue().createdAt()).isEqualTo(FIXED);
    }

    private static Card withId(Card c) {
        return new Card(1L, c.boardId(), c.columnId(), c.number(), c.title(), c.description(),
                c.positionInColumn(), c.archived(), c.movedToDoneAt(), c.createdBy(), c.createdAt(),
                c.updatedAt(), c.type(), c.parentId(), c.shortcode());
    }
}
