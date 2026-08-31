package org.mwolff.manban.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.LabelService;
import org.mwolff.manban.card.domain.Label;

/** Unit-Tests des Label-Controllers (Service gemockt). */
class LabelControllerTest {

  private LabelService service;
  private LabelController controller;

  @BeforeEach
  void setUp() {
    service = mock(LabelService.class);
    controller = new LabelController(service);
  }

  @Test
  void list_mapsLabelsToViews() {
    when(service.list(3L, 2L)).thenReturn(List.of(new Label(1L, 2L, "Bug", "#f00", true)));

    List<LabelController.LabelView> result = controller.list(3L, 2L);

    assertThat(result)
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v.id()).isEqualTo(1L);
              assertThat(v.name()).isEqualTo("Bug");
              assertThat(v.color()).isEqualTo("#f00");
              assertThat(v.countOnEpicTile()).isTrue();
            });
  }

  @Test
  void create_delegatesAndReturnsView() {
    when(service.create(3L, 2L, "Bug", "#f00")).thenReturn(new Label(1L, 2L, "Bug", "#f00", false));

    LabelController.LabelView view =
        controller.create(3L, 2L, new LabelController.LabelRequest("Bug", "#f00", null));

    assertThat(view.id()).isEqualTo(1L);
    assertThat(view.countOnEpicTile()).isFalse();
  }

  /**
   * Neue Labels starten immer mit {@code false} (Entscheidung Manne, 2026-08-31). {@code POST} und
   * {@code PATCH} teilen sich denselben Request-Record — der Anlege-Pfad muss das Feld ausdrücklich
   * verwerfen, statt es durchzureichen.
   */
  @Test
  void create_ignoresCountOnEpicTile() {
    when(service.create(3L, 2L, "Bug", "#f00")).thenReturn(new Label(1L, 2L, "Bug", "#f00", false));

    LabelController.LabelView view =
        controller.create(3L, 2L, new LabelController.LabelRequest("Bug", "#f00", true));

    verify(service).create(3L, 2L, "Bug", "#f00");
    assertThat(view.countOnEpicTile()).isFalse();
  }

  @Test
  void update_delegatesAndReturnsView() {
    when(service.update(3L, 9L, "Defekt", "#00f", null))
        .thenReturn(new Label(9L, 2L, "Defekt", "#00f", false));

    LabelController.LabelView view =
        controller.update(3L, 9L, new LabelController.LabelRequest("Defekt", "#00f", null));

    assertThat(view.name()).isEqualTo("Defekt");
  }

  @Test
  void update_passesCountOnEpicTile() {
    when(service.update(3L, 9L, "Bug", "#f00", true))
        .thenReturn(new Label(9L, 2L, "Bug", "#f00", true));

    LabelController.LabelView view =
        controller.update(3L, 9L, new LabelController.LabelRequest("Bug", "#f00", true));

    assertThat(view.countOnEpicTile()).isTrue();
  }

  /**
   * Das Feld ist im Request dreiwertig (true / false / fehlend). Explizites {@code false} muss den
   * Service erreichen — würde es als "fehlend" behandelt, wäre ein einmal gesetztes Label nicht
   * mehr abschaltbar.
   */
  @Test
  void update_passesExplicitFalse() {
    when(service.update(3L, 9L, "Bug", "#f00", false))
        .thenReturn(new Label(9L, 2L, "Bug", "#f00", false));

    LabelController.LabelView view =
        controller.update(3L, 9L, new LabelController.LabelRequest("Bug", "#f00", false));

    verify(service).update(3L, 9L, "Bug", "#f00", false);
    assertThat(view.countOnEpicTile()).isFalse();
  }

  @Test
  void delete_delegatesToService() {
    controller.delete(3L, 9L);

    verify(service).delete(3L, 9L);
  }
}
