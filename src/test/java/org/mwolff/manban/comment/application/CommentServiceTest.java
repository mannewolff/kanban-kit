package org.mwolff.manban.comment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.auth.application.UserSummary;
import org.mwolff.manban.card.application.ActorContext;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.FremdKartenSchreiber.FremderAkteur;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.comment.application.CommentService.SpiegelNotiz;
import org.mwolff.manban.comment.domain.Comment;
import org.mwolff.manban.comment.domain.LaufKommentar;
import org.mwolff.manban.project.application.BoardQuelle;
import org.mwolff.manban.project.application.FremdesBoardException;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.domain.Permission;

/** Verhaltenstests der Kommentar-Use-Cases (Mockito an den Ports). */
// PMD.TooManyMethods: je Use-Case und Regel ein kleiner @Test; mit der Herkunft (Issue #1620)
// kamen die Stempel-Fälle dazu, die das Paket ausdrücklich hier verlangt.
@SuppressWarnings("PMD.TooManyMethods")
class CommentServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final Instant LAUF = Instant.parse("2026-10-05T08:58:22.123Z");

  private CommentRepository comments;
  private CardService cardService;
  private PermissionChecker permissions;
  private UserLookup users;
  private ActorContext actor;
  private BoardQuelle boardQuelle;
  private CommentService service;

  private static Comment comment(Long authorUserId) {
    return new Comment(3L, 5L, authorUserId, "Ada", "Hallo", FIXED, FIXED, null, null, null, null);
  }

  @BeforeEach
  void setUp() {
    comments = mock(CommentRepository.class);
    cardService = mock(CardService.class);
    permissions = mock(PermissionChecker.class);
    users = mock(UserLookup.class);
    actor = mock(ActorContext.class);
    when(actor.current()).thenReturn(ActorStamp.unknown());
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    boardQuelle = mock(BoardQuelle.class);
    service =
        new CommentService(comments, cardService, permissions, users, actor, clock, boardQuelle);
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
  void create_ueberSession_stempeltDieHerkunftSession() {
    // Given (Issue #1620, A4): dieselbe Quelle wie der Verlauf — der ActorContext.
    when(actor.current()).thenReturn(new ActorStamp(CardActivityOrigin.SESSION, null, null, null));
    when(users.findById(1L)).thenReturn(Optional.of(new UserSummary(1L, "u@x.de", "Ada", true)));
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    CommentService.CommentView view = service.create(1L, 5L, "Hallo");

    // Then
    verify(comments).save(captor.capture());
    assertThat(captor.getValue().origin()).isEqualTo("SESSION");
    assertThat(captor.getValue().tokenName()).isNull();
    assertThat(captor.getValue().agent()).isNull();
    assertThat(view.origin()).isEqualTo("SESSION");
  }

  @Test
  void create_ueberToken_stempeltHerkunftZugangsnameUndModell() {
    // Given
    when(actor.current())
        .thenReturn(new ActorStamp(CardActivityOrigin.TOKEN, "kit-nacht", "claude-opus-5-5", LAUF));
    when(users.findById(1L)).thenReturn(Optional.of(new UserSummary(1L, "u@x.de", "Ada", true)));
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    CommentService.CommentView view = service.create(1L, 5L, "Hallo", LAUF);

    // Then
    verify(comments).save(captor.capture());
    assertThat(captor.getValue().origin()).isEqualTo("TOKEN");
    assertThat(captor.getValue().tokenName()).isEqualTo("kit-nacht");
    assertThat(captor.getValue().agent()).isEqualTo("claude-opus-5-5");
    assertThat(view.origin()).isEqualTo("TOKEN");
    assertThat(view.tokenName()).isEqualTo("kit-nacht");
    assertThat(view.agent()).isEqualTo("claude-opus-5-5");
  }

  @Test
  void create_ohneStempel_speichertKeineHerkunft() {
    // Given (A6): fehlende Herkunft heißt unbekannt, nicht geraten — kein Rückfall auf SESSION.
    when(users.findById(1L)).thenReturn(Optional.of(new UserSummary(1L, "u@x.de", "Ada", true)));
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    CommentService.CommentView view = service.create(1L, 5L, "Hallo");

    // Then
    verify(comments).save(captor.capture());
    assertThat(captor.getValue().origin()).isNull();
    assertThat(captor.getValue().tokenName()).isNull();
    assertThat(captor.getValue().agent()).isNull();
    assertThat(view.origin()).isNull();
    assertThat(view.tokenName()).isNull();
    assertThat(view.agent()).isNull();
  }

  @Test
  void update_ueberToken_laesstDieSessionHerkunftUnveraendert() {
    // Given: die Herkunft gehört dem Anlegen, nicht dem letzten Schreiber (Issue #1620).
    Comment vorhanden =
        new Comment(3L, 5L, 1L, "Ada", "Hallo", FIXED, FIXED, null, "SESSION", null, null);
    when(comments.findById(3L)).thenReturn(Optional.of(vorhanden));
    when(actor.current())
        .thenReturn(new ActorStamp(CardActivityOrigin.TOKEN, "kit-nacht", "claude-opus-5-5", LAUF));
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    CommentService.CommentView view = service.update(1L, 3L, "Geändert", LAUF);

    // Then
    verify(comments).save(captor.capture());
    assertThat(captor.getValue().origin()).isEqualTo("SESSION");
    assertThat(captor.getValue().tokenName()).isNull();
    assertThat(captor.getValue().agent()).isNull();
    assertThat(view.origin()).isEqualTo("SESSION");
  }

  @Test
  void list_liefertDieHerkunftInDerSicht() {
    // Given
    when(comments.findByCardId(5L))
        .thenReturn(
            List.of(
                new Comment(
                    3L, 5L, 1L, "Ada", "Hallo", FIXED, FIXED, null, "TOKEN", "kit", "opus")));

    // When / Then
    assertThat(service.list(1L, 5L))
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v.origin()).isEqualTo("TOKEN");
              assertThat(v.tokenName()).isEqualTo("kit");
              assertThat(v.agent()).isEqualTo("opus");
            });
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
            3L,
            5L,
            1L,
            "Ada",
            "Hallo",
            FIXED,
            FIXED,
            Instant.parse("2026-10-04T00:00:00Z"),
            null,
            null,
            null);
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
    Comment vorhanden =
        new Comment(3L, 5L, 1L, "Ada", "Hallo", FIXED, FIXED, LAUF, null, null, null);
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
    Comment vorhanden =
        new Comment(3L, 5L, 1L, "Ada", "Hallo", FIXED, FIXED, LAUF, null, null, null);
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
  void update_mitKennung_liefertDieViewMitNeuemText() {
    // Given: auch der Ingest-Weg mit Laufkennung gibt den geänderten Kommentar zurück (#1536).
    when(comments.findById(3L)).thenReturn(Optional.of(comment(1L)));
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    CommentService.CommentView view = service.update(1L, 3L, "Geändert", LAUF);

    // Then
    assertThat(view).isNotNull();
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
                new Comment(
                    3L,
                    5L,
                    1L,
                    "Ada",
                    "## Laufstand\n\nplan fertig",
                    FIXED,
                    FIXED,
                    LAUF,
                    null,
                    null,
                    null),
                new Comment(
                    4L,
                    6L,
                    null,
                    "Kit",
                    "## Laufstand\n\nreview begonnen",
                    FIXED,
                    FIXED,
                    null,
                    null,
                    null,
                    null)));

    // When / Then
    assertThat(service.laufstaendeImProjekt(1L))
        .containsExactly(
            new CommentService.LaufstandView(5L, "## Laufstand\n\nplan fertig", LAUF),
            new CommentService.LaufstandView(6L, "## Laufstand\n\nreview begonnen", null));
  }

  @Test
  void trefferAufBoard_liefertJeKarteKommentarIdUndText() {
    // Given (Plan #1574, E16)
    when(comments.findFirstMatchPerCardOnBoard(1L, 10L, "Review"))
        .thenReturn(
            List.of(
                new Comment(3L, 5L, 1L, "Ada", "Ein Review", FIXED, FIXED, null, null, null, null),
                new Comment(
                    4L, 6L, null, "Kit", "Review fertig", FIXED, FIXED, null, null, null, null)));

    // When / Then
    assertThat(service.trefferAufBoard(2L, 1L, 10L, "Review"))
        .containsExactly(
            new CommentService.KommentarTrefferView(5L, 3L, "Ein Review"),
            new CommentService.KommentarTrefferView(6L, 4L, "Review fertig"));
    verify(permissions).requireMembership(2L, 1L);
  }

  @Test
  void trefferAufBoard_ohneMitgliedschaft_liestNichts() {
    // Given
    doThrow(new ProjectAccessDeniedException()).when(permissions).requireMembership(2L, 1L);

    // When / Then
    assertThatThrownBy(() -> service.trefferAufBoard(2L, 1L, 10L, "Review"))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(comments, never()).findFirstMatchPerCardOnBoard(1L, 10L, "Review");
  }

  @Test
  void laufKommentare_liefertJeKommentarKarteUndLaufkennungOhneText() {
    // Given (Issue #1755): Die Auswahl nach Laufkennung trifft der Port; die Fassade reicht
    // Karte und Kennung durch und gibt keinen Text heraus.
    Instant von = LAUF.minusSeconds(3600);
    when(comments.findLaufKommentare(List.of(5L, 6L), von))
        .thenReturn(List.of(new LaufKommentar(5L, LAUF), new LaufKommentar(6L, von)));

    // When / Then
    assertThat(service.laufKommentare(List.of(5L, 6L), von))
        .containsExactly(
            new CommentService.LaufKommentarView(5L, LAUF),
            new CommentService.LaufKommentarView(6L, von));
    assertThat(CommentService.LaufKommentarView.class.getRecordComponents())
        .extracting(RecordComponent::getName)
        .containsExactly("cardId", "laufStart");
  }

  @Test
  void update_ueberSession_anKitMeldung_setztZeitpunktUndKopfzeile() {
    // Given (Issue #2109, Plan #2108 E4): mit Herkunft TOKEN angelegt, von Hand geändert.
    when(comments.findById(3L))
        .thenReturn(Optional.of(kitMeldung("## Übertragung auf den Hauptzweig\n\nWeg: lokal")));
    when(actor.current()).thenReturn(new ActorStamp(CardActivityOrigin.SESSION, null, null, null));

    // When
    Comment gespeichert = aendereUndFange("Von Hand umgeschrieben");

    // Then
    assertThat(gespeichert.vonHandGeaendertAm()).isEqualTo(FIXED);
    assertThat(gespeichert.vonHandKopfzeile()).isEqualTo("## Übertragung auf den Hauptzweig");
    assertThat(gespeichert.body()).isEqualTo("Von Hand umgeschrieben");
  }

  @Test
  void update_ohneStempel_anKitMeldung_zaehltAlsAenderungVonHand() {
    // Given: Nur ein Token gilt als Kit; eine unbekannte Herkunft wird nicht dazu geraten.
    when(comments.findById(3L)).thenReturn(Optional.of(kitMeldung("## Veröffentlichung")));

    // When
    Comment gespeichert = aendereUndFange("Neu");

    // Then
    assertThat(gespeichert.vonHandGeaendertAm()).isEqualTo(FIXED);
    assertThat(gespeichert.vonHandKopfzeile()).isEqualTo("## Veröffentlichung");
  }

  @Test
  void update_ueberToken_anKitMeldung_setztNichts() {
    // Given
    when(comments.findById(3L)).thenReturn(Optional.of(kitMeldung("## Veröffentlichung")));
    when(actor.current())
        .thenReturn(new ActorStamp(CardActivityOrigin.TOKEN, "kit-nacht", "claude-opus-5-5", LAUF));

    // When
    Comment gespeichert = aendereUndFange("## Veröffentlichung, neu");

    // Then
    assertThat(gespeichert.vonHandGeaendertAm()).isNull();
    assertThat(gespeichert.vonHandKopfzeile()).isNull();
  }

  @Test
  void update_ueberDenZugangDerApp_zaehltWieJedesToken() {
    // Given (Plan #2108 E5): Der Zugang der Desktop-App trägt die Herkunft TOKEN.
    when(comments.findById(3L)).thenReturn(Optional.of(kitMeldung("## Veröffentlichung")));
    when(actor.current())
        .thenReturn(new ActorStamp(CardActivityOrigin.TOKEN, "desktop", null, null, true));

    // When
    Comment gespeichert = aendereUndFange("Neu");

    // Then
    assertThat(gespeichert.vonHandGeaendertAm()).isNull();
    assertThat(gespeichert.vonHandKopfzeile()).isNull();
  }

  @Test
  void update_ueberToken_nachAenderungVonHand_loeschtBeideFelder() {
    // Given: Der Text stammt danach wieder ganz vom Kit (Kriterium 13).
    when(comments.findById(3L)).thenReturn(Optional.of(vonHandGeaendert("## Veröffentlichung")));
    when(actor.current())
        .thenReturn(new ActorStamp(CardActivityOrigin.TOKEN, "kit-nacht", null, null));

    // When
    Comment gespeichert = aendereUndFange("## Veröffentlichung, neu");

    // Then
    assertThat(gespeichert.vonHandGeaendertAm()).isNull();
    assertThat(gespeichert.vonHandKopfzeile()).isNull();
  }

  @Test
  void update_zweiteAenderungVonHand_behaeltDieKopfzeileDesKits() {
    // Given: Die erste Änderung hat den Kopf entfernt, das Glied bleibt trotzdem zugeordnet.
    when(comments.findById(3L)).thenReturn(Optional.of(vonHandGeaendert("Kopf entfernt")));
    when(actor.current()).thenReturn(new ActorStamp(CardActivityOrigin.SESSION, null, null, null));

    // When
    Comment gespeichert = aendereUndFange("Noch einmal anders");

    // Then
    assertThat(gespeichert.vonHandGeaendertAm()).isEqualTo(FIXED);
    assertThat(gespeichert.vonHandKopfzeile()).isEqualTo("## Kit-Kopf");
  }

  @Test
  void update_setztAlsKopfzeileDieErsteNichtleereZeile() {
    // Given (E3): Auch die Kette liest die erste nichtleere Zeile.
    when(comments.findById(3L)).thenReturn(Optional.of(kitMeldung("\n  \n## Kopf\nText")));

    // When / Then
    assertThat(aendereUndFange("Neu").vonHandKopfzeile()).isEqualTo("## Kopf");
  }

  @Test
  void update_ohneNichtleereZeile_setztDenZeitpunktOhneKopfzeile() {
    // Given
    when(comments.findById(3L)).thenReturn(Optional.of(kitMeldung(" \n ")));

    // When
    Comment gespeichert = aendereUndFange("Neu");

    // Then
    assertThat(gespeichert.vonHandGeaendertAm()).isEqualTo(FIXED);
    assertThat(gespeichert.vonHandKopfzeile()).isNull();
  }

  @Test
  void update_anSessionKommentar_wirdNieMarkiert() {
    // Given
    Comment vorhanden =
        new Comment(3L, 5L, 1L, "Ada", "## Kopf", FIXED, FIXED, null, "SESSION", null, null);
    when(comments.findById(3L)).thenReturn(Optional.of(vorhanden));
    when(actor.current()).thenReturn(new ActorStamp(CardActivityOrigin.SESSION, null, null, null));

    // When
    Comment gespeichert = aendereUndFange("Neu");

    // Then
    assertThat(gespeichert.vonHandGeaendertAm()).isNull();
    assertThat(gespeichert.vonHandKopfzeile()).isNull();
  }

  @Test
  void update_anKommentarOhneHerkunft_wirdNieMarkiert() {
    // Given: Alt-Kommentare ohne Herkunft gelten nie als Meldung des Kits (E3).
    when(comments.findById(3L)).thenReturn(Optional.of(comment(1L)));

    // When
    Comment gespeichert = aendereUndFange("Neu");

    // Then
    assertThat(gespeichert.vonHandGeaendertAm()).isNull();
    assertThat(gespeichert.vonHandKopfzeile()).isNull();
  }

  @Test
  void create_setztKeineMarke() {
    // Given
    when(users.findById(1L)).thenReturn(Optional.of(new UserSummary(1L, "u@x.de", "Ada", true)));
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    service.create(1L, 5L, "Hallo");

    // Then
    verify(comments).save(captor.capture());
    assertThat(captor.getValue().vonHandGeaendertAm()).isNull();
    assertThat(captor.getValue().vonHandKopfzeile()).isNull();
  }

  @Test
  void kettenKommentare_liefertHerkunftUndMarkeOhneRechtepruefung() {
    // Given (Issue #2109): Vertrag für die Kette, die das Leserecht vorher selbst prüft.
    when(comments.findKettenKommentare(List.of(5L, 6L)))
        .thenReturn(
            List.of(
                vonHandGeaendert("## Kopf"),
                new Comment(4L, 6L, null, "Kit", "Text", FIXED, FIXED, LAUF, "TOKEN", "k", "o")));

    // When / Then
    assertThat(service.kettenKommentare(List.of(5L, 6L)))
        .containsExactly(
            new CommentService.KettenKommentarView(
                3L, 5L, "Kit", "## Kopf", FIXED, "TOKEN", "kit-nacht", "opus", LAUF, "## Kit-Kopf"),
            new CommentService.KettenKommentarView(
                4L, 6L, "Kit", "Text", FIXED, "TOKEN", "k", "o", null, null));
    verifyNoInteractions(permissions);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void projektTraegtKitKommentar_reichtDasErgebnisDerAbfrageDurch(boolean vorhanden) {
    // Given
    when(comments.existsKitKommentarImProjekt(1L, List.of("## Kartenprüfung")))
        .thenReturn(vorhanden);

    // When / Then
    assertThat(service.projektTraegtKitKommentar(1L, List.of("## Kartenprüfung")))
        .isEqualTo(vorhanden);
    verifyNoInteractions(permissions);
  }

  private static Comment kitMeldung(String body) {
    return new Comment(3L, 5L, 1L, "Kit", body, FIXED, FIXED, null, "TOKEN", "kit-nacht", "opus");
  }

  private static Comment vonHandGeaendert(String body) {
    return new Comment(
        3L,
        5L,
        1L,
        "Kit",
        body,
        FIXED,
        FIXED,
        null,
        "TOKEN",
        "kit-nacht",
        "opus",
        LAUF,
        "## Kit-Kopf");
  }

  // --- Spiegel eines fremden Boards (Issue #2162, Plan #2152 A11, A19) ------------------------

  private static final Instant NOTIZ_ANGELEGT = Instant.parse("2026-11-02T09:00:00Z");
  private static final Instant NOTIZ_GEAENDERT = Instant.parse("2026-11-02T11:00:00Z");
  private static final FremderAkteur KIT_KONTO = FremderAkteur.von("Kit-Bot", "kit-bot");
  private static final FremderAkteur ERIKA = FremderAkteur.von("erika", "kit-bot");

  private static SpiegelNotiz notiz(FremderAkteur autor, String text, Instant geaendertAm) {
    return new SpiegelNotiz("501", autor, text, NOTIZ_ANGELEGT, geaendertAm);
  }

  private static Comment gespiegelteMeldung(
      String body, Instant updatedAt, Instant vonHand, String kopf) {
    return new Comment(
        9L,
        5L,
        null,
        "kit-bot",
        body,
        NOTIZ_ANGELEGT,
        updatedAt,
        null,
        "TOKEN",
        null,
        null,
        vonHand,
        kopf,
        "501");
  }

  private Comment spiegleUndFange(SpiegelNotiz notiz) {
    when(boardQuelle.istFremd(1L)).thenReturn(true);
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));
    service.spiegeln(5L, notiz);
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    verify(comments).save(captor.capture());
    return captor.getValue();
  }

  @Test
  void spiegeln_legtNotizDesKitKontosMitHerkunftTokenAn() {
    // Given
    when(comments.findByCardIdAndExternKennung(5L, "501")).thenReturn(Optional.empty());

    // When
    Comment neu = spiegleUndFange(notiz(KIT_KONTO, "## Laufstand\nläuft", NOTIZ_ANGELEGT));

    // Then: Zeiten und Autor aus dem fremden System, kein Benutzer von kanban-kit.
    assertThat(neu.cardId()).isEqualTo(5L);
    assertThat(neu.externKennung()).isEqualTo("501");
    assertThat(neu.authorUserId()).isNull();
    assertThat(neu.authorName()).isEqualTo("Kit-Bot");
    assertThat(neu.body()).isEqualTo("## Laufstand\nläuft");
    assertThat(neu.createdAt()).isEqualTo(NOTIZ_ANGELEGT);
    assertThat(neu.updatedAt()).isEqualTo(NOTIZ_ANGELEGT);
    assertThat(neu.origin()).isEqualTo("TOKEN");
    assertThat(neu.tokenName()).isNull();
    assertThat(neu.laufStart()).isNull();
    assertThat(neu.vonHandGeaendertAm()).isNull();
    verifyNoInteractions(permissions);
  }

  @Test
  void spiegeln_legtNotizEinesMenschenMitHerkunftSessionAn() {
    // Given
    when(comments.findByCardIdAndExternKennung(5L, "501")).thenReturn(Optional.empty());

    // When
    Comment neu = spiegleUndFange(notiz(ERIKA, "Bitte prüfen", NOTIZ_GEAENDERT));

    // Then: Eine geänderte Notiz eines Menschen ist keine geänderte Kit-Meldung.
    assertThat(neu.origin()).isEqualTo("SESSION");
    assertThat(neu.updatedAt()).isEqualTo(NOTIZ_GEAENDERT);
    assertThat(neu.vonHandGeaendertAm()).isNull();
  }

  @Test
  void spiegeln_ohneAutorNameSchreibtUnbekannt() {
    // Given
    when(comments.findByCardIdAndExternKennung(5L, "501")).thenReturn(Optional.empty());

    // When
    Comment neu =
        spiegleUndFange(notiz(FremderAkteur.von(null, "kit-bot"), "Text", NOTIZ_ANGELEGT));

    // Then
    assertThat(neu.authorName()).isEqualTo("Unbekannt");
    assertThat(neu.origin()).isEqualTo("SESSION");
  }

  @Test
  void spiegeln_schonGeaenderteKitMeldungIstBeimErstenUebernehmenGekennzeichnet() {
    // Given
    when(comments.findByCardIdAndExternKennung(5L, "501")).thenReturn(Optional.empty());

    // When
    Comment neu = spiegleUndFange(notiz(KIT_KONTO, "von Hand", NOTIZ_GEAENDERT));

    // Then: Die Fassung des Kits kennt der Spiegel nicht — Marke ja, Kopfzeile nein.
    assertThat(neu.vonHandGeaendertAm()).isEqualTo(NOTIZ_GEAENDERT);
    assertThat(neu.vonHandKopfzeile()).isNull();
  }

  @Test
  @DisplayName("in GitLab geänderte Kit-Meldung ist gekennzeichnet")
  void inGitLabGeaenderteKitMeldungIstGekennzeichnet() {
    // Given: Die Meldung des Kits liegt schon gespiegelt vor.
    when(comments.findByCardIdAndExternKennung(5L, "501"))
        .thenReturn(
            Optional.of(
                gespiegelteMeldung("## Kette angehalten\nFrage", NOTIZ_ANGELEGT, null, null)));

    // When: In GitLab hat sie jemand nach ihrer Anlage geändert.
    Comment geaendert = spiegleUndFange(notiz(KIT_KONTO, "Antwort von Hand", NOTIZ_GEAENDERT));

    // Then: neuer Text, gekennzeichnet wie eine von Hand geänderte Kit-Meldung (A19, #2101 K13).
    assertThat(geaendert.id()).isEqualTo(9L);
    assertThat(geaendert.body()).isEqualTo("Antwort von Hand");
    assertThat(geaendert.updatedAt()).isEqualTo(NOTIZ_GEAENDERT);
    assertThat(geaendert.origin()).isEqualTo("TOKEN");
    assertThat(geaendert.vonHandGeaendertAm()).isEqualTo(NOTIZ_GEAENDERT);
    assertThat(geaendert.vonHandKopfzeile()).isEqualTo("## Kette angehalten");
    assertThat(geaendert.externKennung()).isEqualTo("501");
  }

  @Test
  void spiegeln_weitereAenderungBehaeltDieKopfzeileDerKitFassung() {
    // Given
    Instant spaeter = NOTIZ_GEAENDERT.plusSeconds(600);
    when(comments.findByCardIdAndExternKennung(5L, "501"))
        .thenReturn(
            Optional.of(
                gespiegelteMeldung(
                    "Antwort von Hand", NOTIZ_GEAENDERT, NOTIZ_GEAENDERT, "## Kette angehalten")));

    // When
    Comment geaendert = spiegleUndFange(notiz(KIT_KONTO, "noch einmal", spaeter));

    // Then
    assertThat(geaendert.vonHandGeaendertAm()).isEqualTo(spaeter);
    assertThat(geaendert.vonHandKopfzeile()).isEqualTo("## Kette angehalten");
  }

  @Test
  void spiegeln_unveraenderteNotizSchreibtNichts() {
    // Given
    when(boardQuelle.istFremd(1L)).thenReturn(true);
    when(comments.findByCardIdAndExternKennung(5L, "501"))
        .thenReturn(Optional.of(gespiegelteMeldung("Text", NOTIZ_ANGELEGT, null, null)));

    // When
    service.spiegeln(5L, notiz(KIT_KONTO, "Text", NOTIZ_ANGELEGT));

    // Then
    verify(comments, never()).save(any());
  }

  @Test
  void spiegeln_gleicherTextMitNeuerZeitWirdUebernommen() {
    // Given
    when(comments.findByCardIdAndExternKennung(5L, "501"))
        .thenReturn(Optional.of(gespiegelteMeldung("Text", NOTIZ_ANGELEGT, null, null)));

    // When
    Comment geaendert = spiegleUndFange(notiz(KIT_KONTO, "Text", NOTIZ_GEAENDERT));

    // Then
    assertThat(geaendert.updatedAt()).isEqualTo(NOTIZ_GEAENDERT);
    assertThat(geaendert.vonHandGeaendertAm()).isEqualTo(NOTIZ_GEAENDERT);
  }

  @Test
  void spiegeln_neuerTextOhneSpaetereAenderungszeitBleibtUngekennzeichnet() {
    // Given
    when(comments.findByCardIdAndExternKennung(5L, "501"))
        .thenReturn(Optional.of(gespiegelteMeldung("alt", NOTIZ_ANGELEGT, null, null)));

    // When
    Comment geaendert = spiegleUndFange(notiz(KIT_KONTO, "neu", NOTIZ_ANGELEGT));

    // Then
    assertThat(geaendert.body()).isEqualTo("neu");
    assertThat(geaendert.vonHandGeaendertAm()).isNull();
    assertThat(geaendert.vonHandKopfzeile()).isNull();
  }

  @Test
  void spiegeln_geaenderteNotizEinesMenschenBleibtUngekennzeichnet() {
    // Given
    Comment mensch =
        new Comment(
            9L,
            5L,
            null,
            "erika",
            "alt",
            NOTIZ_ANGELEGT,
            NOTIZ_ANGELEGT,
            null,
            "SESSION",
            null,
            null,
            null,
            null,
            "501");
    when(comments.findByCardIdAndExternKennung(5L, "501")).thenReturn(Optional.of(mensch));

    // When
    Comment geaendert = spiegleUndFange(notiz(ERIKA, "neu", NOTIZ_GEAENDERT));

    // Then
    assertThat(geaendert.body()).isEqualTo("neu");
    assertThat(geaendert.vonHandGeaendertAm()).isNull();
    assertThat(geaendert.vonHandKopfzeile()).isNull();
  }

  @Test
  void spiegeln_schreibtNichtInEinProjektOhneFremdesBoard() {
    // Given
    when(boardQuelle.istFremd(1L)).thenReturn(false);

    // When / Then
    assertThatThrownBy(() -> service.spiegeln(5L, notiz(KIT_KONTO, "Text", NOTIZ_ANGELEGT)))
        .isInstanceOf(IllegalStateException.class);
    verify(comments, never()).save(any());
  }

  @Test
  void spiegelGeloescht_kennzeichnetDieMeldungDesKitsUndLaesstSieStehen() {
    // Given
    when(boardQuelle.istFremd(1L)).thenReturn(true);
    when(comments.findByCardIdAndExternKennung(5L, "501"))
        .thenReturn(
            Optional.of(gespiegelteMeldung("\n## Laufstand\nläuft", NOTIZ_ANGELEGT, null, null)));
    when(comments.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    service.spiegelGeloescht(5L, "501", NOTIZ_GEAENDERT);

    // Then
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    verify(comments).save(captor.capture());
    assertThat(captor.getValue().body()).isEqualTo("\n## Laufstand\nläuft");
    assertThat(captor.getValue().vonHandGeaendertAm()).isEqualTo(NOTIZ_GEAENDERT);
    assertThat(captor.getValue().vonHandKopfzeile()).isEqualTo("## Laufstand");
    verify(comments, never()).deleteById(9L);
  }

  @Test
  void spiegelGeloescht_schonGekennzeichneteMeldungBleibtUnveraendert() {
    // Given
    when(boardQuelle.istFremd(1L)).thenReturn(true);
    when(comments.findByCardIdAndExternKennung(5L, "501"))
        .thenReturn(
            Optional.of(gespiegelteMeldung("x", NOTIZ_ANGELEGT, NOTIZ_ANGELEGT, "## Kopf")));

    // When
    service.spiegelGeloescht(5L, "501", NOTIZ_GEAENDERT);

    // Then
    verify(comments, never()).save(any());
  }

  @Test
  void spiegelGeloescht_notizEinesMenschenUndUnbekannteBleibenUnberuehrt() {
    // Given
    when(boardQuelle.istFremd(1L)).thenReturn(true);
    Comment mensch =
        new Comment(
            9L,
            5L,
            null,
            "erika",
            "x",
            NOTIZ_ANGELEGT,
            NOTIZ_ANGELEGT,
            null,
            "SESSION",
            null,
            null,
            null,
            null,
            "501");
    when(comments.findByCardIdAndExternKennung(5L, "501")).thenReturn(Optional.of(mensch));
    when(comments.findByCardIdAndExternKennung(5L, "502")).thenReturn(Optional.empty());

    // When
    service.spiegelGeloescht(5L, "501", NOTIZ_GEAENDERT);
    service.spiegelGeloescht(5L, "502", NOTIZ_GEAENDERT);

    // Then
    verify(comments, never()).save(any());
    verify(comments, never()).deleteById(any(Long.class));
  }

  @Test
  void spiegelGeloescht_schreibtNichtInEinProjektOhneFremdesBoard() {
    // Given
    when(boardQuelle.istFremd(1L)).thenReturn(false);

    // When / Then
    assertThatThrownBy(() -> service.spiegelGeloescht(5L, "501", NOTIZ_GEAENDERT))
        .isInstanceOf(IllegalStateException.class);
    verify(comments, never()).findByCardIdAndExternKennung(5L, "501");
  }

  @Test
  @DisplayName("Kommentar anlegen wird bei fremdem Board nach der Rechteprüfung abgewiesen")
  void create_lehntBeiFremdemBoardNachDerRechtepruefungAb() {
    // Given: Issue #2164, Plan #2152 A10
    doThrow(new FremdesBoardException()).when(boardQuelle).pruefeSchreibbar(1L);

    // When / Then
    assertThatThrownBy(() -> service.create(1L, 5L, "Hallo", LAUF))
        .isInstanceOf(FremdesBoardException.class);
    InOrder reihenfolge = inOrder(permissions, boardQuelle);
    reihenfolge.verify(permissions).require(1L, 1L, Permission.COMMENT_CREATE);
    reihenfolge.verify(boardQuelle).pruefeSchreibbar(1L);
    verify(comments, never()).save(any(Comment.class));
  }

  @Test
  void create_ohneRechtErfaehrtNichtsVomFremdenBoard() {
    // Given: Wer nicht schreiben darf, sieht 403/404 und erfährt nicht, wo das Board liegt.
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .require(1L, 1L, Permission.COMMENT_CREATE);

    // When / Then
    assertThatThrownBy(() -> service.create(1L, 5L, "Hallo"))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(boardQuelle, never()).pruefeSchreibbar(1L);
  }

  @Test
  @DisplayName("Kommentar ändern wird bei fremdem Board abgewiesen")
  void update_lehntBeiFremdemBoardAb() {
    // Given
    when(comments.findById(3L)).thenReturn(Optional.of(comment(1L)));
    doThrow(new FremdesBoardException()).when(boardQuelle).pruefeSchreibbar(1L);

    // When / Then
    assertThatThrownBy(() -> service.update(1L, 3L, "Neu", LAUF))
        .isInstanceOf(FremdesBoardException.class);
    InOrder reihenfolge = inOrder(permissions, boardQuelle);
    reihenfolge.verify(permissions).require(1L, 1L, Permission.COMMENT_UPDATE);
    reihenfolge.verify(boardQuelle).pruefeSchreibbar(1L);
    verify(comments, never()).save(any(Comment.class));
  }

  @Test
  @DisplayName("Kommentar löschen wird bei fremdem Board abgewiesen")
  void delete_lehntBeiFremdemBoardAb() {
    // Given
    when(comments.findById(3L)).thenReturn(Optional.of(comment(1L)));
    doThrow(new FremdesBoardException()).when(boardQuelle).pruefeSchreibbar(1L);

    // When / Then
    assertThatThrownBy(() -> service.delete(1L, 3L)).isInstanceOf(FremdesBoardException.class);
    InOrder reihenfolge = inOrder(permissions, boardQuelle);
    reihenfolge.verify(permissions).require(1L, 1L, Permission.COMMENT_DELETE);
    reihenfolge.verify(boardQuelle).pruefeSchreibbar(1L);
    verify(comments, never()).deleteById(3L);
  }

  @Test
  void spiegeln_umgehtDieSchreibsperre() {
    // Given: Der Spiegel ist der eine Schreibweg, der bei fremdem Board schreiben darf.
    when(boardQuelle.istFremd(1L)).thenReturn(true);
    when(comments.findByCardIdAndExternKennung(5L, "501")).thenReturn(Optional.empty());
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    service.spiegeln(
        5L,
        new SpiegelNotiz(
            "501", new FremderAkteur("Ada", false), "Text", NOTIZ_GEAENDERT, NOTIZ_GEAENDERT));

    // Then
    verify(boardQuelle, never()).pruefeSchreibbar(1L);
    verify(comments).save(any(Comment.class));
  }

  private Comment aendereUndFange(String body) {
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    service.update(1L, 3L, body);
    verify(comments).save(captor.capture());
    return captor.getValue();
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
        c.laufStart(),
        c.origin(),
        c.tokenName(),
        c.agent(),
        c.vonHandGeaendertAm(),
        c.vonHandKopfzeile(),
        c.externKennung());
  }

  @Test
  void spiegelFehlendeKennzeichnen_kennzeichnetNurFehlendeGespiegelteMeldungen() {
    // Given: ein eigener Kommentar ohne Kennung, eine fehlende und eine vorhandene Meldung
    when(boardQuelle.istFremd(1L)).thenReturn(true);
    Comment fehlt = gespiegelteMeldung("Weg", NOTIZ_ANGELEGT, null, null);
    Comment bleibt =
        new Comment(
            10L,
            5L,
            null,
            "kit-bot",
            "Da",
            NOTIZ_ANGELEGT,
            NOTIZ_ANGELEGT,
            null,
            "TOKEN",
            null,
            null,
            null,
            null,
            "502");
    when(comments.findByCardId(5L)).thenReturn(List.of(comment(1L), fehlt, bleibt));
    when(comments.save(any(Comment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    service.spiegelFehlendeKennzeichnen(5L, List.of("502"), FIXED);

    // Then
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    verify(comments).save(captor.capture());
    assertThat(captor.getValue().externKennung()).isEqualTo("501");
    assertThat(captor.getValue().vonHandGeaendertAm()).isEqualTo(FIXED);
  }

  @Test
  void spiegelFehlendeKennzeichnen_schonGekennzeichnetBleibtUnberuehrt() {
    when(boardQuelle.istFremd(1L)).thenReturn(true);
    Comment schon = gespiegelteMeldung("Weg", NOTIZ_ANGELEGT, NOTIZ_ANGELEGT, "## Kopf");
    when(comments.findByCardId(5L)).thenReturn(List.of(schon));

    service.spiegelFehlendeKennzeichnen(5L, List.of(), FIXED);

    verify(comments, never()).save(any());
  }

  @Test
  void spiegelFehlendeKennzeichnen_ohneFremdesBoardScheitert() {
    when(boardQuelle.istFremd(1L)).thenReturn(false);

    assertThatThrownBy(() -> service.spiegelFehlendeKennzeichnen(5L, List.of(), FIXED))
        .isInstanceOf(IllegalStateException.class);
    verify(comments, never()).findByCardId(5L);
  }
}
