package org.mwolff.manban.card.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.CleanupProperties;

/** Unit-Tests des Config-Controllers. */
class ConfigControllerTest {

  @Test
  void config_exposesDoneRetentionDays() {
    // Given
    var cleanup = new CleanupProperties(true, 30, "0 0 3 * * *");
    var controller = new ConfigController(cleanup);

    // When
    ConfigController.ConfigView result = controller.config();

    // Then
    assertThat(result.doneRetentionDays()).isEqualTo(30);
  }
}
