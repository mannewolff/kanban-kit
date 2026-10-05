package org.mwolff.manban.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.ColumnNotFoundException;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.CardView;
import org.mwolff.manban.card.application.EpicService;
import org.mwolff.manban.card.application.LabelAction;
import org.mwolff.manban.card.domain.CardType;

/** Unit-Tests des Karten-/Epic-Controllers (Service gemockt). */
class CardControllerTest {

  private static final java.time.Instant INSTANT = java.time.Instant.parse("2026-01-01T00:00:00Z");

  private CardService service;
  private EpicService epics;
  private CardController controller;

  private static CardView card() {
    return new CardView(
        1L,
        2L,
        3L,
        4,
        "Title",
        "Desc",
        null,
        0,
        false,
        null,
        List.of(),
        CardType.CARD,
        null,
        null,
        List.of(),
        null,
        List.of(),
        null,
        "BACKLOG",
        true);
  }

  @BeforeEach
  void setUp() {
    service = mock(CardService.class);
    epics = mock(EpicService.class);
    controller = new CardController(service, epics);
  }

  @Test
  void create_epicType_delegatesToCreateEpic() {
    // Given
    CardView view = card();
    var request =
        new CardController.CreateCardRequest(
            null, "Epic", "Desc", null, CardType.EPIC, null, "EP-1", null, null, null, null);
    when(epics.createEpic(3L, 2L, "Epic", "Desc", "EP-1")).thenReturn(view);

    // When
    CardView result = controller.create(3L, 2L, request);

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void create_nullTypeDefaultsToCard_delegatesToCreate() {
    // Given
    CardView view = card();
    var deps = List.of(1, 2);
    var request =
        new CardController.CreateCardRequest(
            7L, "Title", "Desc", deps, null, 9L, null, null, null, null, null);
    when(service.create(3L, 2L, 7L, "Title", "Desc", deps, 9L, null, null, null, null))
        .thenReturn(view);

    // When
    CardView result = controller.create(3L, 2L, request);

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void create_cardTypeWithoutColumn_throwsColumnNotFound() {
    // Given
    var request =
        new CardController.CreateCardRequest(
            null, "Title", "Desc", null, CardType.CARD, null, null, null, null, null, null);

    // When / Then
    assertThatThrownBy(() -> controller.create(3L, 2L, request))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  @Test
  void create_cardType_reichtFaelligkeitZustaendigeUndLabelsDurch() {
    // Given: voller Feldsatz im Anlege-Request
    CardView view = card();
    var due = java.time.Instant.parse("2026-02-01T00:00:00Z");
    var request =
        new CardController.CreateCardRequest(
            7L,
            "Title",
            "Desc",
            null,
            CardType.CARD,
            null,
            null,
            due,
            List.of(4L, 5L),
            List.of(6L),
            null);
    when(service.create(
            3L, 2L, 7L, "Title", "Desc", null, null, due, List.of(4L, 5L), List.of(6L), null))
        .thenReturn(view);

    // When
    CardView result = controller.create(3L, 2L, request);

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void list_delegatesToService() {
    // Given
    List<CardView> views = List.of(card());
    when(service.listByBoard(3L, 2L)).thenReturn(views);

    // When
    List<CardView> result = controller.list(3L, 2L);

    // Then
    assertThat(result).isSameAs(views);
  }

  @Test
  void get_delegatesToService() {
    // Given
    CardView view = card();
    when(service.getCard(3L, 1L)).thenReturn(view);

    // When
    CardView result = controller.get(3L, 1L);

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void update_delegatesToService() {
    // Given
    CardView view = card();
    var deps = List.of(3, 4);
    var request = new CardController.UpdateCardRequest("Title", "Desc", deps, "SC-1", 9L, null);
    when(service.update(3L, 8L, "Title", "Desc", deps, "SC-1", 9L, null)).thenReturn(view);

    // When
    CardView result = controller.update(3L, 8L, request);

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void bulkLabels_delegatesToService() {
    // Given
    List<CardView> views = List.of(card());
    var request = new CardController.BulkLabelsRequest(List.of(8L, 9L), 5L, LabelAction.ADD);
    when(service.bulkLabels(3L, List.of(8L, 9L), 5L, LabelAction.ADD)).thenReturn(views);

    // When
    List<CardView> result = controller.bulkLabels(3L, request);

    // Then
    assertThat(result).isSameAs(views);
  }

  @Test
  void bulkLabels_reichtDieAktionUnveraendertDurch() {
    // Gegenprobe: REMOVE darf unterwegs nicht zu ADD werden — beide Richtungen laufen über
    // denselben Endpunkt, und nur die Aktion unterscheidet sie.
    var request = new CardController.BulkLabelsRequest(List.of(8L), 5L, LabelAction.REMOVE);
    when(service.bulkLabels(3L, List.of(8L), 5L, LabelAction.REMOVE)).thenReturn(List.of());

    controller.bulkLabels(3L, request);

    verify(service).bulkLabels(3L, List.of(8L), 5L, LabelAction.REMOVE);
  }

  @Test
  void setAssignees_delegatesToService() {
    // Given
    CardView view = card();
    when(service.setAssignees(3L, 8L, List.of(5L, 6L))).thenReturn(view);

    // When
    CardView result =
        controller.setAssignees(3L, 8L, new CardController.AssigneesRequest(List.of(5L, 6L)));

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void setAssignees_coalescesNullListToEmpty() {
    // Given
    CardView view = card();
    when(service.setAssignees(3L, 8L, List.of())).thenReturn(view);

    // When
    CardView result = controller.setAssignees(3L, 8L, new CardController.AssigneesRequest(null));

    // Then
    assertThat(result).isSameAs(view);
    verify(service).setAssignees(3L, 8L, List.of());
  }

  @Test
  void setLabels_delegatesToService() {
    CardView view = card();
    when(service.setLabels(3L, 8L, List.of(5L, 6L))).thenReturn(view);

    CardView result =
        controller.setLabels(3L, 8L, new CardController.LabelsRequest(List.of(5L, 6L)));

    assertThat(result).isSameAs(view);
  }

  @Test
  void setLabels_coalescesNullListToEmpty() {
    CardView view = card();
    when(service.setLabels(3L, 8L, List.of())).thenReturn(view);

    CardView result = controller.setLabels(3L, 8L, new CardController.LabelsRequest(null));

    assertThat(result).isSameAs(view);
    verify(service).setLabels(3L, 8L, List.of());
  }

  @Test
  void activity_mapsDomainToViews() {
    // Die Domänen-Abbildung liegt seit #876 in der Fassade; der Controller übernimmt ihre Sicht.
    var entry =
        new CardService.ActivityView(
            5L, 9L, "MOVED", "Verschoben", INSTANT, "TOKEN", "Nachtlauf", "claude-opus-5");
    when(service.listActivityViews(3L, 8L)).thenReturn(List.of(entry));

    List<CardController.ActivityView> result = controller.activity(3L, 8L);

    assertThat(result)
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v.id()).isEqualTo(5L);
              assertThat(v.actorUserId()).isEqualTo(9L);
              assertThat(v.type()).isEqualTo("MOVED");
              assertThat(v.detail()).isEqualTo("Verschoben");
              assertThat(v.createdAt()).isEqualTo(INSTANT);
              assertThat(v.origin()).isEqualTo("TOKEN");
              assertThat(v.tokenName()).isEqualTo("Nachtlauf");
              assertThat(v.agent()).isEqualTo("claude-opus-5");
            });
  }

  @Test
  void activity_mapsLegacyEntryWithoutOrigin() {
    // Alt-Eintrag vor V23: kein Herkunfts-Stempel — die View trägt null statt eines Platzhalters.
    var entry =
        new CardService.ActivityView(5L, 9L, "MOVED", "Verschoben", INSTANT, null, null, null);
    when(service.listActivityViews(3L, 8L)).thenReturn(List.of(entry));

    List<CardController.ActivityView> result = controller.activity(3L, 8L);

    assertThat(result)
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v.origin()).isNull();
              assertThat(v.tokenName()).isNull();
              assertThat(v.agent()).isNull();
            });
  }
}
