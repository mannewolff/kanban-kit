package org.mwolff.manban.comment.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.card.application.ActorContext;
import org.mwolff.manban.card.application.Anbietersicht;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.comment.domain.Comment;
import org.mwolff.manban.project.application.BoardQuelle;
import org.mwolff.manban.project.application.PermissionChecker;

/**
 * Der Spiegel entfernt Kommentare, deren Notiz im fremden System intern ist (Issue #2200): Der
 * Zugang des Spiegels darf dort mehr lesen als ein Mitglied des Projekts.
 */
class CommentServiceVertraulichTest {

  private static final Instant ANGELEGT = Instant.parse("2026-11-02T10:00:00Z");

  private CommentRepository comments;
  private BoardQuelle boardQuelle;
  private CommentService service;

  @BeforeEach
  void setUp() {
    comments = mock(CommentRepository.class);
    CardService cardService = mock(CardService.class);
    boardQuelle = mock(BoardQuelle.class);
    service =
        new CommentService(
            comments,
            cardService,
            mock(PermissionChecker.class),
            mock(UserLookup.class),
            mock(ActorContext.class),
            Clock.fixed(ANGELEGT, ZoneOffset.UTC),
            boardQuelle,
            mock(Anbietersicht.class));
    when(cardService.requireProjectId(5L)).thenReturn(1L);
  }

  @Test
  void loeschtGespiegelteKommentare_auchEineMeldungDesKits_undUebergehtUnbekannte() {
    // Given: Notiz 501 (Meldung des Kits) ist in GitLab intern geworden, 502 nie übernommen.
    when(boardQuelle.istFremd(1L)).thenReturn(true);
    Comment meldung =
        new Comment(
            9L, 5L, null, "kit-bot", "Geheim", ANGELEGT, ANGELEGT, null, "TOKEN", null, null, null,
            null, "501");
    when(comments.findByCardIdAndExternKennung(5L, "501")).thenReturn(Optional.of(meldung));
    when(comments.findByCardIdAndExternKennung(5L, "502")).thenReturn(Optional.empty());

    // When
    service.spiegelVertraulicheEntfernen(5L, List.of("501", "502"));

    // Then
    verify(comments).deleteById(9L);
    verify(comments, never()).save(any());
  }

  @Test
  void ohneKennungen_liestNichts() {
    service.spiegelVertraulicheEntfernen(5L, List.of());

    verifyNoInteractions(comments, boardQuelle);
  }

  @Test
  void ohneFremdesBoard_scheitert_undLoeschtNichts() {
    when(boardQuelle.istFremd(1L)).thenReturn(false);

    assertThatThrownBy(() -> service.spiegelVertraulicheEntfernen(5L, List.of("501")))
        .isInstanceOf(IllegalStateException.class);
    verify(comments, never()).deleteById(any(Long.class));
  }
}
