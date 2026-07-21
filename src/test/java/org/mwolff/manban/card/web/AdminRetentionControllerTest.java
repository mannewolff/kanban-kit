package org.mwolff.manban.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.DoneRetentionSettingService;
import org.mwolff.manban.card.application.DoneRetentionSettingService.RetentionSettings;

/** Unit-Tests des Admin-Retention-Controllers (Delegation + View-Mapping). */
class AdminRetentionControllerTest {

  private static final long ADMIN = 1L;

  @Test
  void get_mapsEffectiveAndOverride() {
    var service = mock(DoneRetentionSettingService.class);
    when(service.currentFor(ADMIN)).thenReturn(new RetentionSettings(7, 7));
    var controller = new AdminRetentionController(service);

    var view = controller.get(ADMIN);

    assertThat(view.effective()).isEqualTo(7);
    assertThat(view.override()).isEqualTo(7);
  }

  @Test
  void update_delegatesToServiceAndMapsView() {
    var service = mock(DoneRetentionSettingService.class);
    when(service.updateOverride(ADMIN, 0)).thenReturn(new RetentionSettings(0, 0));
    var controller = new AdminRetentionController(service);

    var view = controller.update(ADMIN, new AdminRetentionController.UpdateRetentionRequest(0));

    verify(service).updateOverride(ADMIN, 0);
    assertThat(view.effective()).isZero();
    assertThat(view.override()).isZero();
  }
}
