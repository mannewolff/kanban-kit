package org.mwolff.manban.kanbancompat.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.kanbancompat.application.KanbanArchivService;
import org.mwolff.manban.kanbancompat.application.KanbanArchivService.ItemDetail;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Activity;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Comment;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Created;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Epic;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Item;
import org.mwolff.manban.kanbancompat.application.TokenNotBoundException;
import org.springframework.security.core.Authentication;

/** Unit-Tests des Kanban-Compat-Controllers (Service + Authentication gemockt). */
class KanbanCompatControllerTest {

  private static final KanbanPrincipal PRINCIPAL = new KanbanPrincipal(7L, 1L, 2L, 3L, "Token");
  private static final Instant LAUF = Instant.parse("2026-10-05T08:58:22.123Z");

  private KanbanCompatService service;
  private KanbanArchivService archiv;
  private KanbanCompatController controller;

  private static Authentication boundAuthentication() {
    Authentication authentication = mock(Authentication.class);
    when(authentication.getDetails()).thenReturn(PRINCIPAL);
    return authentication;
  }

  @BeforeEach
  void setUp() {
    service = mock(KanbanCompatService.class);
    archiv = mock(KanbanArchivService.class);
    controller = new KanbanCompatController(service, archiv);
  }

  @Test
  void items_withBoundPrincipal_delegates() {
    // Given
    Map<String, List<Item>> items = Map.of();
    when(service.items(PRINCIPAL)).thenReturn(items);

    // When
    Map<String, List<Item>> result = controller.items(boundAuthentication());

    // Then
    assertThat(result).isSameAs(items);
  }

  @Test
  void create_withBoundPrincipal_delegates_defaultsDirectToFalse() {
    // Given: fehlendes direct (null) -> false an den Service
    Created created = new Created(42L, 7, true);
    var request =
        new KanbanCompatController.CreateItemRequest(
            "Title", "Body", "todo", null, null, null, null, null);
    when(service.create(PRINCIPAL, "Title", "Body", "todo", null, false, null, null, null))
        .thenReturn(created);

    // When
    Created result = controller.create(boundAuthentication(), request, null);

    // Then
    assertThat(result).isSameAs(created);
  }

  @Test
  void create_withDirectTrue_delegatesFlag() {
    // Given: direct=true entscheidet im Service die Strenge der Spaltenauflösung (Issue #1203)
    Created created = new Created(45L, 10, true);
    var request =
        new KanbanCompatController.CreateItemRequest(
            "Title", "Body", "READY", null, null, true, null, null);
    when(service.create(PRINCIPAL, "Title", "Body", "READY", null, true, null, null, null))
        .thenReturn(created);

    // When
    Created result = controller.create(boundAuthentication(), request, null);

    // Then
    assertThat(result).isSameAs(created);
  }

  @Test
  void create_acceptsIdeaStored_butDoesNotPassItOn() {
    // Given (Issue #1203): `ideaStored` bleibt im Vertrag zulaessig — aeltere Kit-Versionen senden
    // es —, erreicht den Service aber nicht mehr. Der Aufruf ist feldgleich mit dem ohne das Feld.
    Created created = new Created(43L, 8, true);
    var withFlag =
        new KanbanCompatController.CreateItemRequest(
            "Idee", "Body", "todo", true, null, null, null, null);
    when(service.create(PRINCIPAL, "Idee", "Body", "todo", null, false, null, null, null))
        .thenReturn(created);

    // When
    Created result = controller.create(boundAuthentication(), withFlag, null);

    // Then: kein Fehler, dieselbe Delegation wie ohne das Feld
    assertThat(result).isSameAs(created);
    verify(service).create(PRINCIPAL, "Idee", "Body", "todo", null, false, null, null, null);
  }

  @Test
  void create_passesTheIdempotencyKeyHeader() {
    // Given (Issue #1001)
    Created created = new Created(44L, 9, true);
    var request =
        new KanbanCompatController.CreateItemRequest(
            "Title", "Body", null, null, null, null, null, null);
    when(service.create(PRINCIPAL, "Title", "Body", null, null, false, null, null, "k-1"))
        .thenReturn(created);

    // When
    Created result = controller.create(boundAuthentication(), request, "k-1");

    // Then
    assertThat(result).isSameAs(created);
  }

  @Test
  void comment_passesTheIdempotencyKeyHeader() {
    controller.comment(
        boundAuthentication(), 8L, new KanbanCompatController.CommentRequest("hello"), "k-2", null);

    verify(service).comment(PRINCIPAL, 8L, "hello", "k-2", null);
  }

  @Test
  void move_withBoundPrincipal_delegates() {
    // Given
    var request = new KanbanCompatController.MoveRequest("done", 2);

    // When
    controller.move(boundAuthentication(), 8L, request);

    // Then
    verify(service).move(PRINCIPAL, 8L, "done", 2);
  }

  @Test
  void comment_withBoundPrincipal_delegates() {
    // Given
    var request = new KanbanCompatController.CommentRequest("hello");

    // When
    controller.comment(boundAuthentication(), 8L, request, null, null);

    // Then
    verify(service).comment(PRINCIPAL, 8L, "hello", null, null);
  }

  @Test
  void comment_mitGueltigemNachtlaufHeader_reichtDieLaufkennungDurch() {
    // Issue #1428: X-Night-Run kommt als Instant beim Service an.
    var request = new KanbanCompatController.CommentRequest("hello");

    controller.comment(boundAuthentication(), 8L, request, null, " 2026-10-05T08:58:22.123Z ");

    verify(service).comment(PRINCIPAL, 8L, "hello", null, LAUF);
  }

  @Test
  void comment_mitUngueltigemNachtlaufHeader_reichtNullDurch() {
    var request = new KanbanCompatController.CommentRequest("hello");

    controller.comment(boundAuthentication(), 8L, request, null, "gestern abend");

    verify(service).comment(PRINCIPAL, 8L, "hello", null, null);
  }

  @Test
  void updateComment_mitGueltigemNachtlaufHeader_reichtDieLaufkennungDurch() {
    var request = new KanbanCompatController.CommentRequest("neu");

    controller.updateComment(boundAuthentication(), 8L, 9L, request, "2026-10-05T08:58:22.123Z");

    verify(service).updateComment(PRINCIPAL, 8L, 9L, "neu", LAUF);
  }

  @Test
  void updateComment_mitUngueltigemNachtlaufHeader_reichtNullDurch() {
    var request = new KanbanCompatController.CommentRequest("neu");

    controller.updateComment(boundAuthentication(), 8L, 9L, request, "kein Zeitpunkt");

    verify(service).updateComment(PRINCIPAL, 8L, 9L, "neu", null);
  }

  @Test
  void updateComment_withBoundPrincipal_delegates() {
    // Given
    var request = new KanbanCompatController.CommentRequest("neu");

    // When
    controller.updateComment(boundAuthentication(), 8L, 9L, request, null);

    // Then
    verify(service).updateComment(PRINCIPAL, 8L, 9L, "neu", null);
  }

  @Test
  void addLabel_withBoundPrincipal_delegates() {
    // Given
    var request = new KanbanCompatController.LabelRequest("kit:nightrun");

    // When
    controller.addLabel(boundAuthentication(), 8L, request);

    // Then
    verify(service).addLabel(PRINCIPAL, 8L, "kit:nightrun");
  }

  @Test
  void removeLabel_withBoundPrincipal_delegates() {
    // When: der Name steht im Query-Parameter, nicht im Pfad (er darf Slashes enthalten)
    controller.removeLabel(boundAuthentication(), 8L, "kit:night/run");

    // Then
    verify(service).removeLabel(PRINCIPAL, 8L, "kit:night/run");
  }

  @Test
  void comments_withBoundPrincipal_delegates() {
    // Given
    List<Comment> comments =
        List.of(new Comment(9L, "Anna", "Hallo", Instant.parse("2026-01-01T10:00:00Z")));
    when(service.listComments(PRINCIPAL, 8L)).thenReturn(comments);

    // When
    List<Comment> result = controller.comments(boundAuthentication(), 8L);

    // Then
    assertThat(result).isSameAs(comments);
  }

  @Test
  void activity_withBoundPrincipal_delegates() {
    // Given
    List<Activity> activity =
        List.of(
            new Activity(
                9L,
                3L,
                "CREATED",
                "Angelegt",
                Instant.parse("2026-01-01T10:00:00Z"),
                "TOKEN",
                "Nachtlauf",
                "claude-opus-5"));
    when(service.listActivity(PRINCIPAL, 8L)).thenReturn(activity);

    // When
    List<Activity> result = controller.activity(boundAuthentication(), 8L);

    // Then
    assertThat(result).isSameAs(activity);
  }

  @Test
  void activity_nullAuthentication_throwsTokenNotBound() {
    // When / Then: ohne Bindung endet der Aufruf wie bei jedem anderen Endpunkt mit 409
    assertThatThrownBy(() -> controller.activity(null, 8L))
        .isInstanceOf(TokenNotBoundException.class);
  }

  @Test
  void itemByNumber_withBoundPrincipal_delegates() {
    // Given (Plan #1574, A2)
    ItemDetail detail =
        new ItemDetail(
            new Item(8L, 42, "T", null, "DONE", 0, "card", List.of(), null, null),
            true,
            Instant.parse("2026-01-01T10:00:00Z"),
            null,
            List.of());
    when(archiv.itemByNumber(PRINCIPAL, 42)).thenReturn(detail);

    // When
    ItemDetail result = controller.itemByNumber(boundAuthentication(), 42);

    // Then
    assertThat(result).isSameAs(detail);
  }

  @Test
  void itemByNumber_karteFehlt_bleibtNichtGefunden() {
    // Given: unbekannt, anderes Board und Papierkorb enden im Service gleich (A3) -> 404
    when(archiv.itemByNumber(PRINCIPAL, 99)).thenThrow(new CardNotFoundException());

    // When / Then
    assertThatThrownBy(() -> controller.itemByNumber(boundAuthentication(), 99))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void itemByNumber_nullAuthentication_throwsTokenNotBound() {
    // When / Then: ohne Bindung 409 wie bei jedem anderen Endpunkt
    assertThatThrownBy(() -> controller.itemByNumber(null, 42))
        .isInstanceOf(TokenNotBoundException.class);
  }

  @Test
  void epics_withBoundPrincipal_delegates() {
    // Given
    List<Epic> epics = List.of();
    when(service.epics(PRINCIPAL)).thenReturn(epics);

    // When
    List<Epic> result = controller.epics(boundAuthentication());

    // Then
    assertThat(result).isSameAs(epics);
  }

  @Test
  void items_nullAuthentication_throwsTokenNotBound() {
    // When / Then
    assertThatThrownBy(() -> controller.items(null)).isInstanceOf(TokenNotBoundException.class);
  }

  @Test
  void items_detailsNotKanbanPrincipal_throwsTokenNotBound() {
    // Given
    Authentication authentication = mock(Authentication.class);
    when(authentication.getDetails()).thenReturn("not-a-principal");

    // When / Then
    assertThatThrownBy(() -> controller.items(authentication))
        .isInstanceOf(TokenNotBoundException.class);
  }
}
