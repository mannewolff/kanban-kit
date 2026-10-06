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
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.auth.application.UserSummary;
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.comment.domain.Comment;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.domain.Permission;

/** Verhaltenstests der Kommentar-Use-Cases (Mockito an den Ports). */
class CommentServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final Instant LAUF = Instant.parse("2026-10-05T08:58:22.123Z");

  private CommentRepository comments;
  private CardService cardService;
  private PermissionChecker permissions;
  private UserLookup users;
  private CommentService service;

  private static Comment comment(Long authorUserId) {
    return new Comment(3L, 5L, authorUserId, "Ada", "Hallo", FIXED, FIXED, null);
  }

  @BeforeEach
  void setUp() {
    comments = mock(CommentRepository.class);
    cardService = mock(CardService.class);
    permissions = mock(PermissionChecker.class);
    users = mock(UserLookup.class);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    service = new CommentService(comments, cardService, permissions, users, clock);
    when(cardService.requireProjectId(5L)).thenReturn(1L);
  }

  @Test
  void create_setsCreatedAtFromInjectedClock() {
    // Given
    when(users.findById(1L)).thenReturn(Optional.of(new UserSummary(1L, "u@x.de", "Ada", true)));
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    service.create(1L, 5L, "Hallo");

    // Then
    verify(comments).save(captor.capture());
    assertThat(captor.getValue().createdAt()).isEqualTo(FIXED);
  }

  @Test
  void create_ohneKennung_speichertKeineLaufkennung() {
    // Given: Session-Kommentare über die dreistellige Signatur tragen nie eine Kennung (A3).
    when(users.findById(1L)).thenReturn(Optional.of(new UserSummary(1L, "u@x.de", "Ada", true)));
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    service.create(1L, 5L, "Hallo");

    // Then
    verify(comments).save(captor.capture());
    assertThat(captor.getValue().laufStart()).isNull();
  }

  @Test
  void create_mitKennung_speichertDieLaufkennung() {
    // Given
    when(users.findById(1L)).thenReturn(Optional.of(new UserSummary(1L, "u@x.de", "Ada", true)));
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    CommentService.CommentView view = service.create(1L, 5L, "Hallo", LAUF);

    // Then
    verify(comments).save(captor.capture());
    assertThat(captor.getValue().laufStart()).isEqualTo(LAUF);
    assertThat(captor.getValue().body()).isEqualTo("Hallo");
    assertThat(view.body()).isEqualTo("Hallo");
  }

  @Test
  void create_usesAuthorDisplayName() {
    // Given
    when(users.findById(1L)).thenReturn(Optional.of(new UserSummary(1L, "u@x.de", "Ada", true)));
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

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
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

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
    when(users.findById(1L)).thenReturn(Optional.of(new UserSummary(1L, "u@x.de", "Ada", true)));
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    CommentService.CommentView view = service.create(1L, 5L, "Hallo");

    // Then
    assertThat(view.body()).isEqualTo("Hallo");
  }

  @Test
  void create_throwsCardNotFound_whenCardUnknown() {
    // Given: die card-Fassade meldet die unbekannte Karte — der Kommentar-Service reicht sie durch.
    when(cardService.requireProjectId(5L)).thenThrow(new CardNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.create(1L, 5L, "Hallo"))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void create_checksPermissionAgainstProjectFromCardFacade() {
    // #405/#458: die Projekt-ID kommt aus der card-Fassade (auch board-lose Pool-Ideen tragen
    // eine) und wird unveraendert fuer die Rechtepruefung genutzt. Eine abweichende ID belegt das
    // Durchreichen — eine anderweitig hergeleitete ID fiele hier auf.
    when(cardService.requireProjectId(5L)).thenReturn(42L);
    when(users.findById(1L)).thenReturn(Optional.of(new UserSummary(1L, "u@x.de", "Ada", true)));
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    CommentService.CommentView view = service.create(1L, 5L, "Hallo");

    verify(permissions).require(1L, 42L, Permission.COMMENT_CREATE);
    assertThat(view.body()).isEqualTo("Hallo");
  }

  @Test
  void list_checksMembershipAgainstProjectFromCardFacade() {
    // Lesen prueft die Mitgliedschaft im Projekt der Karte — ebenfalls ueber die card-Fassade.
    when(cardService.requireProjectId(5L)).thenReturn(42L);
    when(comments.findByCardId(5L)).thenReturn(List.of(comment(1L)));

    service.list(1L, 5L);

    verify(permissions).requireMembership(1L, 42L);
  }

  @Test
  void list_mapsCommentsToViews() {
    // Given
    when(comments.findByCardId(5L)).thenReturn(List.of(comment(1L)));

    // When
    List<CommentService.CommentView> views = service.list(1L, 5L);

    // Then
    assertThat(views)
        .singleElement()
        .extracting(CommentService.CommentView::body)
        .isEqualTo("Hallo");
  }

  @Test
  void update_persistsNewBody_whenAuthorEditsOwnComment() {
    // Given
    when(comments.findById(3L)).thenReturn(Optional.of(comment(1L)));
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    service.update(1L, 3L, "Geändert");

    // Then
    verify(comments).save(captor.capture());
    assertThat(captor.getValue().body()).isEqualTo("Geändert");
  }

  @Test
  void update_mitKennung_ueberschreibtDieLaufkennung() {
    // Given: der Laufstand gehört dem letzten Schreiber (E3).
    Comment vorhanden =
        new Comment(
            3L, 5L, 1L, "Ada", "Hallo", FIXED, FIXED, Instant.parse("2026-10-04T00:00:00Z"));
    when(comments.findById(3L)).thenReturn(Optional.of(vorhanden));
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    service.update(1L, 3L, "Geändert", LAUF);

    // Then
    verify(comments).save(captor.capture());
    assertThat(captor.getValue().laufStart()).isEqualTo(LAUF);
    assertThat(captor.getValue().body()).isEqualTo("Geändert");
  }

  @Test
  void update_ueberschreibtDieKennungMitNull() {
    // Given: ein nicht ausgewiesener letzter Schreiber ist ein letzter Schreiber (E3).
    Comment vorhanden = new Comment(3L, 5L, 1L, "Ada", "Hallo", FIXED, FIXED, LAUF);
    when(comments.findById(3L)).thenReturn(Optional.of(vorhanden));
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    service.update(1L, 3L, "Geändert", null);

    // Then
    verify(comments).save(captor.capture());
    assertThat(captor.getValue().laufStart()).isNull();
  }

  @Test
  void update_ohneKennung_setztDieKennungAufNull() {
    // Given: die dreistellige Signatur des Session-Wegs gibt null weiter (A3).
    Comment vorhanden = new Comment(3L, 5L, 1L, "Ada", "Hallo", FIXED, FIXED, LAUF);
    when(comments.findById(3L)).thenReturn(Optional.of(vorhanden));
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    service.update(1L, 3L, "Geändert");

    // Then
    verify(comments).save(captor.capture());
    assertThat(captor.getValue().laufStart()).isNull();
  }

  @Test
  void update_returnsViewWithNewBody() {
    // Given
    when(comments.findById(3L)).thenReturn(Optional.of(comment(1L)));
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

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
    assertThatThrownBy(() -> service.update(1L, 3L, "x"))
        .isInstanceOf(CommentNotFoundException.class);
  }

  @Test
  void update_throwsAccessDenied_whenEditorIsNotAuthor() {
    // Given
    when(comments.findById(3L)).thenReturn(Optional.of(comment(99L)));

    // When / Then
    assertThatThrownBy(() -> service.update(1L, 3L, "x"))
        .isInstanceOf(ProjectAccessDeniedException.class);
  }

  @Test
  void update_throwsAccessDenied_whenCommentHasNoAuthor() {
    // Given
    when(comments.findById(3L)).thenReturn(Optional.of(comment(null)));

    // When / Then
    assertThatThrownBy(() -> service.update(1L, 3L, "x"))
        .isInstanceOf(ProjectAccessDeniedException.class);
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

  @Test
  void laufstaendeImProjekt_liefertJeKommentarKarteBodyUndLaufkennung() {
    // Given
    when(comments.findLaufstaendeImProjekt(1L))
        .thenReturn(
            List.of(
                new Comment(3L, 5L, 1L, "Ada", "## Laufstand\n\nplan fertig", FIXED, FIXED, LAUF),
                new Comment(
                    4L, 6L, null, "Kit", "## Laufstand\n\nreview begonnen", FIXED, FIXED, null)));

    // When / Then
    assertThat(service.laufstaendeImProjekt(1L))
        .containsExactly(
            new CommentService.LaufstandView(5L, "## Laufstand\n\nplan fertig", LAUF),
            new CommentService.LaufstandView(6L, "## Laufstand\n\nreview begonnen", null));
  }

  /** Simuliert die DB: vergibt beim ersten Speichern eine ID (Issue #0080). */
  private static Comment saved(Comment c) {
    if (c.id() != null) {
      return c;
    }
    return new Comment(
        7L,
        c.cardId(),
        c.authorUserId(),
        c.authorName(),
        c.body(),
        c.createdAt(),
        c.updatedAt(),
        c.laufStart());
  }
}
