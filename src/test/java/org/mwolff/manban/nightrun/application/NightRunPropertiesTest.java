package org.mwolff.manban.nightrun.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests der Defaulting-Logik im Kompaktkonstruktor von {@link NightRunProperties}. */
class NightRunPropertiesTest {

  @Test
  void appliesDefault_whenValueMissing() {
    // When
    NightRunProperties props = new NightRunProperties(null);

    // Then
    assertThat(props.maxPerProject()).isEqualTo(30);
  }

  @Test
  void appliesDefault_whenValueBelowOne() {
    // When
    NightRunProperties props = new NightRunProperties(0);

    // Then
    assertThat(props.maxPerProject()).isEqualTo(30);
  }

  @Test
  void keepsSmallestValidValue() {
    // 1 ist gueltig und darf nicht auf den Default fallen — die Grenze liegt darunter.
    NightRunProperties props = new NightRunProperties(1);

    assertThat(props.maxPerProject()).isEqualTo(1);
  }

  @Test
  void keepsProvidedValue() {
    // When
    NightRunProperties props = new NightRunProperties(5);

    // Then
    assertThat(props.maxPerProject()).isEqualTo(5);
  }
}
