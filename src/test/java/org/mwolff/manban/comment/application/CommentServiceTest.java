package org.mwolff.manban.comment.application;

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
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.board.application.BoardRepository;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.comment.domain.Comment;
import org.mwolff.manban.project.application.PermissionChecker;

/** Zeit-Test: der Anlege-Zeitstempel eines Kommentars stammt aus der injizierten Clock. */
class CommentServiceTest {

    private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void create_setsCreatedAtFromInjectedClock() {
        // Given
        CommentRepository comments = mock(CommentRepository.class);
        CardRepository cards = mock(CardRepository.class);
        BoardRepository boards = mock(BoardRepository.class);
        PermissionChecker permissions = mock(PermissionChecker.class);
        AppUserRepository users = mock(AppUserRepository.class);
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
        when(cards.findById(5L)).thenReturn(Optional.of(new Card(5L, 10L, 20L, 1, "T", null, 0,
                false, null, 1L, FIXED, FIXED, CardType.CARD, null, null)));
        when(boards.findById(10L)).thenReturn(Optional.of(new Board(10L, 1L, "B", FIXED)));
        when(users.findById(1L)).thenReturn(Optional.of(
                new AppUser(1L, "u@x.de", "hash", "Ada", true, PlatformRole.USER)));
        when(comments.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));
        CommentService service = new CommentService(comments, cards, boards, permissions, users, clock);

        // When
        service.create(1L, 5L, "Hallo");

        // Then
        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(comments).save(captor.capture());
        assertThat(captor.getValue().createdAt()).isEqualTo(FIXED);
    }
}
