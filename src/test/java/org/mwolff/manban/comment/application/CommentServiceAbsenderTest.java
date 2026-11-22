package org.mwolff.manban.comment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.auth.application.UserSummary;
import org.mwolff.manban.card.application.ActorContext;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.FremdKartenSchreiber.FremderAkteur;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.comment.application.CommentService.SpiegelNotiz;
import org.mwolff.manban.comment.domain.Comment;
import org.mwolff.manban.project.application.BoardQuelle;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.domain.Permission;

/**
 * Absender eines Kommentars und Schreibschutz der Antwort (Issue #2208, Plan #2206 E6, E7): Art des
 * Zugangs, Beitrag der KI und die Sperre für Antworten über einen gewöhnlichen Zugang.
 */
class CommentServiceAbsenderTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final Instant NOTIZ_ANGELEGT = Instant.parse("2026-11-02T09:00:00Z");

  private CommentRepository comments;
  private PermissionChecker permissions;
  private UserLookup users;
  private ActorContext actor;
  private BoardQuelle boardQuelle;
  private CommentService service;

  @BeforeEach
  void setUp() {
    comments = mock(CommentRepository.class);
    CardService cardService = mock(CardService.class);
    permissions = mock(PermissionChecker.class);
    users = mock(UserLookup.class);
    actor = mock(ActorContext.class);
    boardQuelle = mock(BoardQuelle.class);
    service =
        new CommentService(
            comments,
            cardService,
            permissions,
            users,
            actor,
            Clock.fixed(FIXED, ZoneOffset.UTC),
            boardQuelle);
    when(cardService.requireProjectId(5L)).thenReturn(1L);
  }

  private static Comment comment(Long authorUserId) {
    return new Comment(3L, 5L, authorUserId, "Ada", "Hallo", FIXED, FIXED, null, null, null, null);
  }

  private Comment aendereUndFange(String body) {
    when(comments.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    service.update(1L, 3L, body);
    verify(comments).save(captor.capture());
    return captor.getValue();
  }

  /** Simuliert die DB: vergibt beim ersten Speichern eine ID. */
  private static Comment saved(Comment c) {
    return new Comment(
        7L,
        c.cardId(),
        c.authorUserId(),
        c.authorName(),
        c.body(),
        c.createdAt(),
        c.updatedAt(),
        c.laufStart(),
        c.origin(),
        c.tokenName(),
        c.agent(),
        c.vonHandGeaendertAm(),
        c.vonHandKopfzeile(),
        c.externKennung(),
        c.zugangArt(),
        c.vonKi());
  }

  private static final ActorStamp STANDARD_ZUGANG =
      new ActorStamp(CardActivityOrigin.TOKEN, "anbieter", null, null, false);
  private static final ActorStamp APP_ZUGANG =
      new ActorStamp(CardActivityOrigin.TOKEN, "desktop", null, null, true);
  private static final ActorStamp SITZUNG =
      new ActorStamp(CardActivityOrigin.SESSION, null, null, null);

  private Comment legeAnUndFange(ActorStamp stamp, String body) {
    when(actor.current()).thenReturn(stamp);
    when(users.findById(1L)).thenReturn(Optional.of(new UserSummary(1L, "u@x.de", "Ada", true)));
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    CommentService.CommentView view = service.create(1L, 5L, body);
    verify(comments).save(captor.capture());
    assertThat(view.zugangArt()).isEqualTo(captor.getValue().zugangArt());
    assertThat(view.vonKi()).isEqualTo(captor.getValue().vonKi());
    return captor.getValue();
  }

  @Test
  void create_ueberDenStandardZugangEinesOwners_istVonDerKi() {
    // Given: Der Inhaber des Zugangs ist echter OWNER oder ADMIN des Projekts.
    when(permissions.istEchterOwnerOderAdmin(1L, 1L)).thenReturn(true);

    // When
    Comment gespeichert = legeAnUndFange(STANDARD_ZUGANG, "## Gespräch\nHallo");

    // Then
    assertThat(gespeichert.zugangArt()).isEqualTo("STANDARD");
    assertThat(gespeichert.vonKi()).isTrue();
  }

  @Test
  void create_ueberDenStandardZugangEinesMitglieds_istNichtVonDerKi() {
    // Given: Ein gewöhnlicher Zugang darf auch einem Kunden gehören (E6).
    when(permissions.istEchterOwnerOderAdmin(1L, 1L)).thenReturn(false);

    // When
    Comment gespeichert = legeAnUndFange(STANDARD_ZUGANG, "## Gespräch\nHallo");

    // Then
    assertThat(gespeichert.zugangArt()).isEqualTo("STANDARD");
    assertThat(gespeichert.vonKi()).isFalse();
  }

  @Test
  void create_ueberDieSitzungEinesOwners_istNichtVonDerKi() {
    // Given
    when(permissions.istEchterOwnerOderAdmin(1L, 1L)).thenReturn(true);

    // When
    Comment gespeichert = legeAnUndFange(SITZUNG, "## Gespräch\nHallo");

    // Then
    assertThat(gespeichert.zugangArt()).isNull();
    assertThat(gespeichert.vonKi()).isFalse();
  }

  @Test
  void create_ueberDenZugangDerAppEinesOwners_istNichtVonDerKi() {
    // Given
    when(permissions.istEchterOwnerOderAdmin(1L, 1L)).thenReturn(true);

    // When
    Comment gespeichert = legeAnUndFange(APP_ZUGANG, "## Gespräch\nHallo");

    // Then
    assertThat(gespeichert.zugangArt()).isEqualTo("APP");
    assertThat(gespeichert.vonKi()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "## Antwort\nFrage 1: A",
        "\n\n## Antwort\nFrage 1: A",
        "   \n  ## antwort",
        "## Answer",
        "## ANSWER\nQuestion 1: B",
        "## Antwort auf Frage 1"
      })
  void create_antwortUeberDenStandardZugang_wirdAbgewiesen(String body) {
    // Given: auch ein Owner — das Rechenzentrum antwortet sich nicht selbst (E7).
    when(actor.current()).thenReturn(STANDARD_ZUGANG);
    when(permissions.istEchterOwnerOderAdmin(1L, 1L)).thenReturn(true);

    // When / Then
    assertThatThrownBy(() -> service.create(1L, 5L, body))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(comments, never()).save(any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void create_antwortUeberSitzungOderApp_entsteht(boolean app) {
    // When
    Comment gespeichert = legeAnUndFange(app ? APP_ZUGANG : SITZUNG, "\n## Antwort\nFrage 1: A");

    // Then
    assertThat(gespeichert.body()).isEqualTo("\n## Antwort\nFrage 1: A");
  }

  @Test
  void create_antwortErstInDerZweitenZeile_istKeinKopf() {
    // When
    Comment gespeichert = legeAnUndFange(STANDARD_ZUGANG, "## Gespräch\n## Antwort");

    // Then
    assertThat(gespeichert.body()).isEqualTo("## Gespräch\n## Antwort");
  }

  @Test
  void create_antwortUeberDenStandardZugang_ohneRechtBleibtEsBeimFehlendenRecht() {
    // Given: Die Rechteprüfung kommt zuerst, die Sperre ändert an ihr nichts.
    when(actor.current()).thenReturn(STANDARD_ZUGANG);
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .require(1L, 1L, Permission.COMMENT_CREATE);

    // When / Then
    assertThatThrownBy(() -> service.create(1L, 5L, "## Antwort"))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(comments, never()).save(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"## Antwort\nFrage 1: A", "\n  ## answer"})
  void update_zurAntwortUeberDenStandardZugang_wirdAbgewiesen(String body) {
    // Given: ein eigener Kommentar, der nachträglich zur Antwort würde
    when(comments.findById(3L)).thenReturn(Optional.of(comment(1L)));
    when(actor.current()).thenReturn(STANDARD_ZUGANG);

    // When / Then
    assertThatThrownBy(() -> service.update(1L, 3L, body))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(comments, never()).save(any());
  }

  @Test
  void update_zurAntwortUeberDieSitzung_entsteht() {
    // Given
    when(comments.findById(3L)).thenReturn(Optional.of(comment(1L)));
    when(actor.current()).thenReturn(SITZUNG);

    // When
    Comment gespeichert = aendereUndFange("## Antwort\nFrage 1: A");

    // Then
    assertThat(gespeichert.body()).isEqualTo("## Antwort\nFrage 1: A");
  }

  @Test
  void update_behaeltZugangsartUndKiDesAnlegens() {
    // Given: Der Absender steht beim Anlegen fest; eine Änderung über die Sitzung ändert ihn nicht.
    Comment vonDerKi =
        new Comment(
            3L,
            5L,
            1L,
            "Ada",
            "## Gespräch",
            FIXED,
            FIXED,
            null,
            "TOKEN",
            "anbieter",
            null,
            null,
            null,
            null,
            "STANDARD",
            true);
    when(comments.findById(3L)).thenReturn(Optional.of(vonDerKi));
    when(actor.current()).thenReturn(SITZUNG);

    // When
    Comment gespeichert = aendereUndFange("## Gespräch\nkorrigiert");

    // Then
    assertThat(gespeichert.zugangArt()).isEqualTo("STANDARD");
    assertThat(gespeichert.vonKi()).isTrue();
  }

  @Test
  void spiegeln_neueNotiz_hatKeineZugangsartUndIstNichtVonDerKi() {
    // Given
    when(boardQuelle.istFremd(1L)).thenReturn(true);
    when(comments.findByCardIdAndExternKennung(5L, "601")).thenReturn(Optional.empty());

    // When
    service.spiegeln(
        5L,
        new SpiegelNotiz(
            "601", new FremderAkteur("kit", true), "## Gespräch", NOTIZ_ANGELEGT, NOTIZ_ANGELEGT));

    // Then
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    verify(comments).save(captor.capture());
    assertThat(captor.getValue().zugangArt()).isNull();
    assertThat(captor.getValue().vonKi()).isFalse();
  }
}
