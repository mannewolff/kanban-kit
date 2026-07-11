package org.mwolff.manban.project.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.project.application.RoleMatrixService;
import org.mwolff.manban.project.application.RoleMatrixService.RoleMatrixView;

/** Unit-Tests des Rollen-Matrix-Controllers (Service gemockt). */
class RolesControllerTest {

  @Test
  void matrix_delegatesToService() {
    // Given
    RoleMatrixService service = mock(RoleMatrixService.class);
    var view = new RoleMatrixView(List.of("OWNER"), List.of(), Map.of());
    when(service.matrix()).thenReturn(view);
    var controller = new RolesController(service);

    // When
    RoleMatrixView result = controller.matrix();

    // Then
    assertThat(result).isSameAs(view);
  }
}
