package org.mwolff.manban.benachrichtigung.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Die Frist für Label-Ereignisse eines Laufs (Issue #2078, Plan #2070 E3). */
class BenachrichtigungPropertiesTest {

  private static final Duration VORGABE = Duration.ofMinutes(10);

  @Test
  void ohneAngabeGiltDieVorgabe() {
    assertThat(new BenachrichtigungProperties(null).laufLabelFrist()).isEqualTo(VORGABE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT0S", "-PT1M"})
  void eineNichtPositiveFristErgibtDieVorgabe(Duration frist) {
    assertThat(new BenachrichtigungProperties(frist).laufLabelFrist()).isEqualTo(VORGABE);
  }

  @Test
  void eineAngegebeneFristBleibt() {
    assertThat(new BenachrichtigungProperties(Duration.ofSeconds(1)).laufLabelFrist())
        .isEqualTo(Duration.ofSeconds(1));
  }
}
