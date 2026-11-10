package org.mwolff.manban.benachrichtigung.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Frist, Takt und Aufbewahrung der Benachrichtigungen (Issues #2078, #2081, Plan #2070). */
class BenachrichtigungPropertiesTest {

  private static final Duration VORGABE = Duration.ofMinutes(10);
  private static final Duration TAKT_VORGABE = Duration.ofMinutes(1);
  private static final int AUFBEWAHRUNG_VORGABE = 90;

  private static BenachrichtigungProperties mitFrist(Duration frist) {
    return new BenachrichtigungProperties(frist, null, 0);
  }

  private static BenachrichtigungProperties mitTakt(Duration takt) {
    return new BenachrichtigungProperties(null, takt, 0);
  }

  private static BenachrichtigungProperties mitAufbewahrung(int tage) {
    return new BenachrichtigungProperties(null, null, tage);
  }

  @Test
  void ohneAngabeGiltDieVorgabe() {
    assertThat(mitFrist(null).laufLabelFrist()).isEqualTo(VORGABE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT0S", "-PT1M"})
  void eineNichtPositiveFristErgibtDieVorgabe(Duration frist) {
    assertThat(mitFrist(frist).laufLabelFrist()).isEqualTo(VORGABE);
  }

  @Test
  void eineAngegebeneFristBleibt() {
    assertThat(mitFrist(Duration.ofSeconds(1)).laufLabelFrist()).isEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void ohneTaktGiltEineMinute() {
    assertThat(mitTakt(null).takt()).isEqualTo(TAKT_VORGABE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT0S", "-PT1M"})
  void einNichtPositiverTaktErgibtDieVorgabe(Duration takt) {
    assertThat(mitTakt(takt).takt()).isEqualTo(TAKT_VORGABE);
  }

  @Test
  void einAngegebenerTaktBleibt() {
    assertThat(mitTakt(Duration.ofSeconds(90)).takt()).isEqualTo(Duration.ofSeconds(90));
  }

  /**
   * Unter drei Tagen fielen Anlässe aus dem Speicher, die das Fenster von 48 Stunden noch sieht.
   */
  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 1, 2})
  void eineZuKurzeAufbewahrungErgibtDieVorgabe(int tage) {
    assertThat(mitAufbewahrung(tage).retentionDays()).isEqualTo(AUFBEWAHRUNG_VORGABE);
  }

  @ParameterizedTest
  @ValueSource(ints = {3, 30})
  void eineAngegebeneAufbewahrungBleibt(int tage) {
    assertThat(mitAufbewahrung(tage).retentionDays()).isEqualTo(tage);
  }
}
