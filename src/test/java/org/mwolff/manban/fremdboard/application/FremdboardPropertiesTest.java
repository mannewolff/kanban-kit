package org.mwolff.manban.fremdboard.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class FremdboardPropertiesTest {

  @Test
  void ohneAngabenGeltenDieVorgaben() {
    FremdboardProperties properties = new FremdboardProperties(null, null, null, null, null);

    assertThat(properties.secretKey()).isNull();
    assertThat(properties.hosts()).containsExactly("gitlab.com");
    assertThat(properties.takt()).isEqualTo(Duration.ofSeconds(60));
    assertThat(properties.mindestabstand()).isEqualTo(Duration.ofMinutes(2));
    assertThat(properties.vollabgleich()).isEqualTo(Duration.ofMinutes(60));
  }

  @Test
  void leereUndNichtPositiveWerteGeltenWieFehlende() {
    FremdboardProperties properties =
        new FremdboardProperties(
            "  ", List.of(" ", ""), Duration.ZERO, Duration.ofSeconds(-1), Duration.ZERO);

    assertThat(properties.secretKey()).isNull();
    assertThat(properties.hosts()).containsExactly("gitlab.com");
    assertThat(properties.takt()).isEqualTo(Duration.ofSeconds(60));
    assertThat(properties.mindestabstand()).isEqualTo(Duration.ofMinutes(2));
    assertThat(properties.vollabgleich()).isEqualTo(Duration.ofMinutes(60));
  }

  @Test
  void gesetzteWerteBleiben() {
    FremdboardProperties properties =
        new FremdboardProperties(
            " abc ",
            List.of(" Git.Example.org ", "", "gitlab.com"),
            Duration.ofSeconds(30),
            Duration.ofMinutes(5),
            Duration.ofMinutes(90));

    assertThat(properties.secretKey()).isEqualTo("abc");
    assertThat(properties.hosts()).containsExactly("git.example.org", "gitlab.com");
    assertThat(properties.takt()).isEqualTo(Duration.ofSeconds(30));
    assertThat(properties.mindestabstand()).isEqualTo(Duration.ofMinutes(5));
    assertThat(properties.vollabgleich()).isEqualTo(Duration.ofMinutes(90));
  }
}
