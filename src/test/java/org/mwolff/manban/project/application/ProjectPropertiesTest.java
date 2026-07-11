package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Tests der Defaulting-Logik im Kompaktkonstruktor von {@link ProjectProperties}. */
class ProjectPropertiesTest {

  @Test
  void appliesDefault_whenTtlNull() {
    // When
    ProjectProperties props = new ProjectProperties(null);

    // Then
    assertThat(props.invitationTtl()).isEqualTo(Duration.ofDays(7));
  }

  @Test
  void keepsProvidedTtl() {
    // When
    ProjectProperties props = new ProjectProperties(Duration.ofDays(3));

    // Then
    assertThat(props.invitationTtl()).isEqualTo(Duration.ofDays(3));
  }
}
