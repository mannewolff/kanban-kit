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
    when(service.list(3L, 2L)).thenReturn(List.of(new Label(1L, 2L, "Bug", "#f00")));

    List<LabelController.LabelView> result = controller.list(3L, 2L);

    assertThat(result)
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v.id()).isEqualTo(1L);
              assertThat(v.name()).isEqualTo("Bug");
              assertThat(v.color()).isEqualTo("#f00");
            });
  }

  @Test
  void create_delegatesAndReturnsView() {
    when(service.create(3L, 2L, "Bug", "#f00")).thenReturn(new Label(1L, 2L, "Bug", "#f00"));

    LabelController.LabelView view =
        controller.create(3L, 2L, new LabelController.LabelRequest("Bug", "#f00"));

    assertThat(view.id()).isEqualTo(1L);
  }

  @Test
  void update_delegatesAndReturnsView() {
    when(service.update(3L, 9L, "Defekt", "#00f")).thenReturn(new Label(9L, 2L, "Defekt", "#00f"));

    LabelController.LabelView view =
        controller.update(3L, 9L, new LabelController.LabelRequest("Defekt", "#00f"));

    assertThat(view.name()).isEqualTo("Defekt");
  }

  @Test
  void delete_delegatesToService() {
    controller.delete(3L, 9L);

    verify(service).delete(3L, 9L);
  }
}
