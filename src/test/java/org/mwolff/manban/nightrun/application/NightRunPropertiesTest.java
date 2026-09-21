package org.mwolff.manban.nightrun.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.nightrun.domain.NightRunKind;

/**
 * Tests der Defaulting-Logik im Kompaktkonstruktor von {@link NightRunProperties} und der Auswahl
 * der Grenze nach Gattung. Der Ersatzwert der Lauf-Aufbewahrung ist seit Issue #935 190 (Plan #933,
 * E7); das Grenzenpaar der interaktiven Sitzung kam mit Issue #1011 dazu.
 */
class NightRunPropertiesTest {

  @Test
  void appliesDefault_whenValueMissing() {
    // When
    NightRunProperties props = new NightRunProperties(null, null, null, null, null);

    // Then
    assertThat(props.maxPerProject()).isEqualTo(190);
  }

  @Test
  void appliesDefault_whenValueBelowOne() {
    // When
    NightRunProperties props = new NightRunProperties(0, 0, 0, 0, null);

    // Then
    assertThat(props.maxPerProject()).isEqualTo(190);
  }

  @Test
  void keepsSmallestValidValue() {
    // 1 ist gueltig und darf nicht auf den Default fallen — die Grenze liegt darunter.
    NightRunProperties props = new NightRunProperties(1, 1, 1, 1, null);

    assertThat(props.maxPerProject()).isEqualTo(1);
  }

  @Test
  void appliesItemDefault_whenValueMissing() {
    assertThat(new NightRunProperties(null, null, null, null, null).maxItemsPerProject())
        .isEqualTo(2000);
  }

  @Test
  void appliesItemDefault_whenValueBelowOne() {
    // Wie bei maxPerProject: Eine 0 hiesse, jedes verwaiste Paket sofort zu loeschen (#966).
    assertThat(new NightRunProperties(null, 0, null, null, null).maxItemsPerProject())
        .isEqualTo(2000);
  }

  @Test
  void keepsSmallestValidItemValue() {
    assertThat(new NightRunProperties(null, 1, null, null, null).maxItemsPerProject()).isEqualTo(1);
  }

  @Test
  void keepsProvidedValue() {
    // When
    NightRunProperties props = new NightRunProperties(45, 7, 46, 8, null);

    // Then
    assertThat(props.maxPerProject()).isEqualTo(45);
    assertThat(props.maxItemsPerProject()).isEqualTo(7);
    assertThat(props.maxInteractivePerProject()).isEqualTo(46);
    assertThat(props.maxInteractiveItemsPerProject()).isEqualTo(8);
  }

  // --- Grenzenpaar der interaktiven Sitzung (Issue #1011) ------------------------------------

  @Test
  void appliesInteractiveDefault_whenValueMissing() {
    assertThat(new NightRunProperties(null, null, null, null, null).maxInteractivePerProject())
        .isEqualTo(400);
  }

  @Test
  void appliesInteractiveDefault_whenValueBelowOne() {
    assertThat(new NightRunProperties(null, null, 0, null, null).maxInteractivePerProject())
        .isEqualTo(400);
  }

  @Test
  void keepsSmallestValidInteractiveValue() {
    assertThat(new NightRunProperties(null, null, 1, null, null).maxInteractivePerProject())
        .isEqualTo(1);
  }

  @Test
  void appliesInteractiveItemDefault_whenValueMissing() {
    assertThat(new NightRunProperties(null, null, null, null, null).maxInteractiveItemsPerProject())
        .isEqualTo(4000);
  }

  @Test
  void appliesInteractiveItemDefault_whenValueBelowOne() {
    assertThat(new NightRunProperties(null, null, null, 0, null).maxInteractiveItemsPerProject())
        .isEqualTo(4000);
  }

  @Test
  void keepsSmallestValidInteractiveItemValue() {
    assertThat(new NightRunProperties(null, null, null, 1, null).maxInteractiveItemsPerProject())
        .isEqualTo(1);
  }

  // --- Auswahl nach Gattung ------------------------------------------------------------------

  @Test
  void maxRunsForWaehltDieGrenzeDerGattung() {
    NightRunProperties props = new NightRunProperties(45, 7, 46, 8, null);

    assertThat(props.maxRunsFor(NightRunKind.NIGHT)).isEqualTo(45);
    assertThat(props.maxRunsFor(NightRunKind.INTERACTIVE)).isEqualTo(46);
  }

  @Test
  void maxOrphanItemsForWaehltDieGrenzeDerGattung() {
    NightRunProperties props = new NightRunProperties(45, 7, 46, 8, null);

    assertThat(props.maxOrphanItemsFor(NightRunKind.NIGHT)).isEqualTo(7);
    assertThat(props.maxOrphanItemsFor(NightRunKind.INTERACTIVE)).isEqualTo(8);
  }

  // --- Stillefrist (Issue #1091) -------------------------------------------------------------

  @Test
  void appliesStilleFristDefault_whenValueMissing() {
    assertThat(new NightRunProperties(null, null, null, null, null).stilleFrist())
        .isEqualTo(Duration.ofMinutes(90));
  }

  @Test
  void appliesStilleFristDefault_whenValueZero() {
    // ZERO hiesse, jeden unfertigen Lauf im selben Augenblick totzusagen.
    assertThat(new NightRunProperties(null, null, null, null, Duration.ZERO).stilleFrist())
        .isEqualTo(Duration.ofMinutes(90));
  }

  @Test
  void appliesStilleFristDefault_whenValueNegative() {
    assertThat(new NightRunProperties(null, null, null, null, Duration.ofMinutes(-1)).stilleFrist())
        .isEqualTo(Duration.ofMinutes(90));
  }

  @Test
  void keepsProvidedStilleFrist() {
    assertThat(new NightRunProperties(null, null, null, null, Duration.ofMinutes(5)).stilleFrist())
        .isEqualTo(Duration.ofMinutes(5));
  }
}
