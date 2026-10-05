package org.mwolff.manban.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.CardMoveService;
import org.mwolff.manban.card.application.CardView;
import org.mwolff.manban.card.application.InvalidStatusException;
import org.mwolff.manban.card.application.SortDirection;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Unit-Tests des Controllers für Verschieben, Status und Umzug (Service gemockt). Die Testmethoden
 * stammen unverändert aus {@link CardControllerTest} (Issue #1395, Plan #1387 E6/E12).
 */
class CardMoveControllerTest {

  private CardMoveService service;
  private CardMoveController controller;

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
    service = mock(CardMoveService.class);
    controller = new CardMoveController(service);
  }

  @Test
  void move_delegatesToService() {
    // Given
    CardView view = card();
    when(service.move(3L, 8L, 5L, 2)).thenReturn(view);

    // When
    CardView result = controller.move(3L, 8L, new CardMoveController.MoveCardRequest(5L, 2));

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void sortByNumber_delegatesToService() {
    // When
    controller.sortByNumber(3L, 8L, new CardMoveController.SortByNumberRequest(SortDirection.DESC));

    // Then
    verify(service).sortColumnByNumber(3L, 8L, SortDirection.DESC);
  }

  @Test
  void bulkTransfer_delegatesToService() {
    // Given
    List<CardView> views = List.of(card());
    var request = new CardMoveController.BulkTransferRequest(List.of(8L, 9L), 20L, 60L);
    when(service.bulkTransfer(3L, List.of(8L, 9L), 20L, 60L)).thenReturn(views);

    // When
    List<CardView> result = controller.bulkTransfer(3L, request);

    // Then
    assertThat(result).isSameAs(views);
  }

  @Test
  void transfer_delegatesToService() {
    // Given
    CardView view = card();
    when(service.transfer(3L, 8L, 20L, 60L)).thenReturn(view);

    // When
    CardView result =
        controller.transfer(3L, 8L, new CardMoveController.TransferCardRequest(20L, 60L));

    // Then
    assertThat(result).isSameAs(view);
  }

  // --- PUT /api/cards/{cardId}/status (Issue #1300) -------------------
  // Die Statuscodes entstehen im GlobalExceptionHandler aus @ResponseStatus; der Endpunkt gegen
  // die echte Kette steht in CardStatusIT.

  @Test
  void setStatus_delegiertUndAntwortet204() throws NoSuchMethodException {
    controller.setStatus(3L, 8L, new CardMoveController.SetStatusRequest("IN_REVIEW"));

    verify(service).setStatus(3L, 8L, "IN_REVIEW");
    Method endpunkt =
        CardMoveController.class.getDeclaredMethod(
            "setStatus", Long.class, long.class, CardMoveController.SetStatusRequest.class);
    assertThat(endpunkt.getAnnotation(PutMapping.class).value())
        .containsExactly("/api/cards/{cardId}/status");
    assertThat(endpunkt.getAnnotation(ResponseStatus.class).value())
        .isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void setStatus_unbekannterWert_ergibt400() {
    doThrow(new InvalidStatusException("Unbekannter Status: FERTIG"))
        .when(service)
        .setStatus(3L, 8L, "FERTIG");

    assertThatThrownBy(
            () -> controller.setStatus(3L, 8L, new CardMoveController.SetStatusRequest("FERTIG")))
        .isInstanceOf(InvalidStatusException.class);
    assertThat(InvalidStatusException.class.getAnnotation(ResponseStatus.class).value())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void setStatus_ohneCardMove_ergibt403() {
    doThrow(new ProjectAccessDeniedException()).when(service).setStatus(3L, 8L, "READY");

    assertThatThrownBy(
            () -> controller.setStatus(3L, 8L, new CardMoveController.SetStatusRequest("READY")))
        .isInstanceOf(ProjectAccessDeniedException.class);
    assertThat(ProjectAccessDeniedException.class.getAnnotation(ResponseStatus.class).value())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }
}
