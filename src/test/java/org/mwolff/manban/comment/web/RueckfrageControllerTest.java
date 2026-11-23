package org.mwolff.manban.comment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.comment.application.RueckfrageService;
import org.mwolff.manban.comment.application.RueckfrageService.AntwortAuftrag;
import org.mwolff.manban.comment.application.RueckfrageService.EinzelAuftrag;
import org.mwolff.manban.comment.application.RueckfrageService.RueckfrageView;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung.RueckfrageZustand;

/** Unit-Tests des Rückfragen-Controllers (Service gemockt, Issue #2210). */
class RueckfrageControllerTest {

  private RueckfrageService service;
  private RueckfrageController controller;

  private static RueckfrageView view() {
    return new RueckfrageView(40L, Instant.EPOCH, RueckfrageZustand.OFFEN, null, null, List.of());
  }

  @BeforeEach
  void setUp() {
    service = mock(RueckfrageService.class);
    controller = new RueckfrageController(service);
  }

  @Test
  void liste_delegiertAnDenService() {
    // Given
    List<RueckfrageView> views = List.of(view());
    when(service.liste(3L, 5L)).thenReturn(views);

    // When / Then
    assertThat(controller.liste(3L, 5L)).isSameAs(views);
  }

  @Test
  void antworten_uebersetztDenKoerper() {
    // Given
    RueckfrageView view = view();
    AntwortAuftrag auftrag =
        new AntwortAuftrag(
            true,
            List.of(
                new EinzelAuftrag(1, 2, null, null),
                new EinzelAuftrag(2, null, "eigene", null),
                new EinzelAuftrag(3, null, null, true)),
            1,
            true,
            "abc");
    when(service.antworten(3L, 5L, 40L, auftrag)).thenReturn(view);
    RueckfrageController.AntwortRequest request =
        new RueckfrageController.AntwortRequest(
            true,
            List.of(
                new RueckfrageController.EinzelantwortRequest(1, 2, null, null),
                new RueckfrageController.EinzelantwortRequest(2, null, "eigene", null),
                new RueckfrageController.EinzelantwortRequest(3, null, null, true)),
            1,
            true,
            "abc");

    // When / Then
    assertThat(controller.antworten(3L, 5L, 40L, request)).isSameAs(view);
  }

  @Test
  void antworten_fehlendeSchalterSindAus() {
    // Given
    RueckfrageView view = view();
    when(service.antworten(3L, 5L, 40L, new AntwortAuftrag(false, List.of(), null, false, null)))
        .thenReturn(view);

    // When / Then
    assertThat(
            controller.antworten(
                3L, 5L, 40L, new RueckfrageController.AntwortRequest(null, null, null, null, null)))
        .isSameAs(view);
  }
}
