package org.mwolff.manban.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.DoneRetentionSettingService;

/** Unit-Tests des Config-Controllers. */
class ConfigControllerTest {

  @Test
  void config_exposesEffectiveDoneRetentionDays() {
    // Given
    var service = mock(DoneRetentionSettingService.class);
    when(service.effectiveRetentionDays()).thenReturn(30);
    var controller = new ConfigController(service);

    // When
    ConfigController.ConfigView result = controller.config();

    // Then
    assertThat(result.doneRetentionDays()).isEqualTo(30);
  }
}
