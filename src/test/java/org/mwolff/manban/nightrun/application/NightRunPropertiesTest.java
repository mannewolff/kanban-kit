package org.mwolff.manban.nightrun.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests der Defaulting-Logik im Kompaktkonstruktor von {@link NightRunProperties}. */
class NightRunPropertiesTest {

  @Test
  void appliesDefault_whenValueMissing() {
    // When
    NightRunProperties props = new NightRunProperties(null, null);

    // Then
    assertThat(props.maxPerProject()).isEqualTo(30);
  }

  @Test
  void appliesDefault_whenValueBelowOne() {
    // When
    NightRunProperties props = new NightRunProperties(0, 0);

    // Then
    assertThat(props.maxPerProject()).isEqualTo(30);
  }

  @Test
  void keepsSmallestValidValue() {
    // 1 ist gueltig und darf nicht auf den Default fallen — die Grenze liegt darunter.
    NightRunProperties props = new NightRunProperties(1, 1);

    assertThat(props.maxPerProject()).isEqualTo(1);
  }

  @Test
  void appliesItemDefault_whenValueMissing() {
    assertThat(new NightRunProperties(null, null).maxItemsPerProject()).isEqualTo(2000);
  }

  @Test
  void appliesItemDefault_whenValueBelowOne() {
    // Wie bei maxPerProject: Eine 0 hiesse, jedes verwaiste Paket sofort zu loeschen (#966).
    assertThat(new NightRunProperties(null, 0).maxItemsPerProject()).isEqualTo(2000);
  }

  @Test
  void keepsSmallestValidItemValue() {
    assertThat(new NightRunProperties(null, 1).maxItemsPerProject()).isEqualTo(1);
  }

  @Test
  void keepsProvidedValue() {
    // When
    NightRunProperties props = new NightRunProperties(5, 7);

    // Then
    assertThat(props.maxPerProject()).isEqualTo(5);
    assertThat(props.maxItemsPerProject()).isEqualTo(7);
  }
}
