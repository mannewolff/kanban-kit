package org.mwolff.manban.attachment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.attachment.application.StorageReconciliationService;
import org.mwolff.manban.attachment.application.StorageReconciliationService.ReconciliationReport;

/** Unit-Tests des Admin-Abgleich-Controllers (Service gemockt). */
class AdminStorageControllerTest {

  private final StorageReconciliationService service = mock(StorageReconciliationService.class);
  private final AdminStorageController controller = new AdminStorageController(service);

  @Test
  void get_returnsTheReportForTheAuthenticatedUser() {
    // Given
    ReconciliationReport report =
        new ReconciliationReport(List.of("cards/1/waise"), List.of("cards/2/weg"));
    when(service.report(3L)).thenReturn(report);

    // When / Then
    assertThat(controller.get(3L)).isSameAs(report);
  }
}
