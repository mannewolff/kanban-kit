package org.mwolff.manban.comment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.board.application.BoardRepository;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.comment.domain.Comment;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.domain.Permission;

/** Verhaltenstests der Kommentar-Use-Cases (Mockito an den Ports). */
class CommentServiceTest {

    private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

    private CommentRepository comments;
    private CardRepository cards;
    private BoardRepository boards;
    private PermissionChecker permissions;
    private AppUserRepository users;
    private CommentService service;

    private static Card card() {
        return new Card(5L, 10L, 20L, 1, "T", null, 0, false, null, 1L, FIXED, FIXED,
                CardType.CARD, null, null);
    }

    private static Comment comment(Long authorUserId) {
        return new Comment(3L, 5L, authorUserId, "Ada", "Hallo", FIXED, FIXED);
    }

    @BeforeEach
    void setUp() {
        comments = mock(CommentRepository.class);
        cards = mock(CardRepository.class);
        boards = mock(BoardRepository.class);
        permissions = mock(PermissionChecker.class);
        users = mock(AppUserRepository.class);
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
        service = new CommentService(comments, cards, boards, permissions, users, clock);
        when(cards.findById(5L)).thenReturn(Optional.of(card()));
        when(boards.findById(10L)).thenReturn(Optional.of(new Board(10L, 1L, "B", FIXED)));
    }

    @Test
    void create_setsCreatedAtFromInjectedClock() {
        // Given
        when(users.findById(1L)).thenReturn(Optional.of(
                new AppUser(1L, "u@x.de", "hash", "Ada", true, PlatformRole.USER)));
        when(comments.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        service.create(1L, 5L, "Hallo");

        // Then
        verify(comments).save(captor.capture());
        assertThat(captor.getValue().createdAt()).isEqualTo(FIXED);
    }

    @Test
    void create_usesAuthorDisplayName() {
        // Given
        when(users.findById(1L)).thenReturn(Optional.of(
                new AppUser(1L, "u@x.de", "hash", "Ada", true, PlatformRole.USER)));
        when(comments.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        service.create(1L, 5L, "Hallo");

        // Then
        verify(comments).save(captor.capture());
        assertThat(captor.getValue().authorName()).isEqualTo("Ada");
    }

    @Test
    void create_fallsBackToUnknownAuthorName_whenUserMissing() {
        // Given
        when(users.findById(1L)).thenReturn(Optional.empty());
        when(comments.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        service.create(1L, 5L, "Hallo");

        // Then
        verify(comments).save(captor.capture());
        assertThat(captor.getValue().authorName()).isEqualTo("Unbekannt");
    }

    @Test
    void create_returnsViewOfPersistedComment() {
        // Given
        when(users.findById(1L)).thenReturn(Optional.of(
                new AppUser(1L, "u@x.de", "hash", "Ada", true, PlatformRole.USER)));
        when(comments.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        CommentService.CommentView view = service.create(1L, 5L, "Hallo");

        // Then
        assertThat(view.body()).isEqualTo("Hallo");
    }

    @Test
    void create_throwsCardNotFound_whenCardUnknown() {
        // Given
        when(cards.findById(5L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.create(1L, 5L, "Hallo")).isInstanceOf(CardNotFoundException.class);
    }

    @Test
    void list_mapsCommentsToViews() {
        // Given
        when(comments.findByCardId(5L)).thenReturn(List.of(comment(1L)));

        // When
        List<CommentService.CommentView> views = service.list(1L, 5L);

        // Then
        assertThat(views).singleElement().extracting(CommentService.CommentView::body).isEqualTo("Hallo");
    }

    @Test
    void update_persistsNewBody_whenAuthorEditsOwnComment() {
        // Given
        when(comments.findById(3L)).thenReturn(Optional.of(comment(1L)));
        when(comments.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        service.update(1L, 3L, "Geändert");

        // Then
        verify(comments).save(captor.capture());
        assertThat(captor.getValue().body()).isEqualTo("Geändert");
    }

    @Test
    void update_returnsViewWithNewBody() {
        // Given
        when(comments.findById(3L)).thenReturn(Optional.of(comment(1L)));
        when(comments.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        CommentService.CommentView view = service.update(1L, 3L, "Geändert");

        // Then
        assertThat(view.body()).isEqualTo("Geändert");
    }

    @Test
    void update_throwsCommentNotFound_whenUnknown() {
        // Given
        when(comments.findById(3L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.update(1L, 3L, "x")).isInstanceOf(CommentNotFoundException.class);
    }

    @Test
    void update_throwsAccessDenied_whenEditorIsNotAuthor() {
        // Given
        when(comments.findById(3L)).thenReturn(Optional.of(comment(99L)));

        // When / Then
        assertThatThrownBy(() -> service.update(1L, 3L, "x")).isInstanceOf(ProjectAccessDeniedException.class);
    }

    @Test
    void update_throwsAccessDenied_whenCommentHasNoAuthor() {
        // Given
        when(comments.findById(3L)).thenReturn(Optional.of(comment(null)));

        // When / Then
        assertThatThrownBy(() -> service.update(1L, 3L, "x")).isInstanceOf(ProjectAccessDeniedException.class);
    }

    @Test
    void delete_removesComment() {
        // Given
        when(comments.findById(3L)).thenReturn(Optional.of(comment(1L)));

        // When
        service.delete(1L, 3L);

        // Then
        verify(comments).deleteById(3L);
    }

    @Test
    void delete_requiresCommentDeletePermission() {
        // Given
        when(comments.findById(3L)).thenReturn(Optional.of(comment(1L)));

        // When
        service.delete(1L, 3L);

        // Then
        verify(permissions).require(1L, 1L, Permission.COMMENT_DELETE);
    }

    @Test
    void delete_throwsCommentNotFound_whenUnknown() {
        // Given
        when(comments.findById(3L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.delete(1L, 3L)).isInstanceOf(CommentNotFoundException.class);
    }
}
