package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Tests der Defaulting-Logik im Kompaktkonstruktor von {@link AuthProperties}. */
class AuthPropertiesTest {

  @Test
  void appliesDefaults_whenAllValuesNull() {
    // When
    AuthProperties props = new AuthProperties(null, null, null, null, null, null, null);

    // Then
    assertThat(props.baseUrl()).isEqualTo("http://localhost:8080");
    assertThat(props.verificationTtl()).isEqualTo(Duration.ofHours(24));
    assertThat(props.resetTtl()).isEqualTo(Duration.ofHours(1));
    assertThat(props.sessionSecret()).isEqualTo(AuthProperties.INSECURE_DEFAULT_SESSION_SECRET);
    assertThat(props.sessionTtl()).isEqualTo(Duration.ofDays(30));
    assertThat(props.cookieSecure()).isTrue();
  }

  @Test
  void appliesDefaults_whenStringsAreBlank() {
    // When
    AuthProperties props = new AuthProperties("   ", null, "   ", null, null, null, null);

    // Then
    assertThat(props.baseUrl()).isEqualTo("http://localhost:8080");
    assertThat(props.sessionSecret()).isEqualTo(AuthProperties.INSECURE_DEFAULT_SESSION_SECRET);
  }

  @Test
  void keepsProvidedValues() {
    // When
    AuthProperties props =
        new AuthProperties(
            "https://manban.example",
            Duration.ofHours(2),
            "s3cr3t",
            Duration.ofDays(1),
            Boolean.FALSE,
            Duration.ofMinutes(30),
            new AuthProperties.LoginCode(Duration.ofMinutes(5), 3, 7));

    // Then
    assertThat(props.baseUrl()).isEqualTo("https://manban.example");
    assertThat(props.verificationTtl()).isEqualTo(Duration.ofHours(2));
    assertThat(props.sessionSecret()).isEqualTo("s3cr3t");
    assertThat(props.sessionTtl()).isEqualTo(Duration.ofDays(1));
    assertThat(props.cookieSecure()).isFalse();
    assertThat(props.resetTtl()).isEqualTo(Duration.ofMinutes(30));
    assertThat(props.loginCode().ttl()).isEqualTo(Duration.ofMinutes(5));
    assertThat(props.loginCode().maxAttempts()).isEqualTo(3);
    assertThat(props.loginCode().maxRequestsPerHour()).isEqualTo(7);
  }

  @Test
  void loginCode_appliesDefaults_whenBlockMissing() {
    // When
    AuthProperties props = new AuthProperties(null, null, null, null, null, null, null);

    // Then — Plan #1801, A6: 10 Minuten, 5 Fehlversuche, 5 Anforderungen je Stunde
    assertThat(props.loginCode().ttl()).isEqualTo(Duration.ofMinutes(10));
    assertThat(props.loginCode().maxAttempts()).isEqualTo(5);
    assertThat(props.loginCode().maxRequestsPerHour()).isEqualTo(5);
  }

  @Test
  void loginCode_appliesDefaults_whenValuesNull() {
    // When
    AuthProperties.LoginCode loginCode = new AuthProperties.LoginCode(null, null, null);

    // Then
    assertThat(loginCode.ttl()).isEqualTo(Duration.ofMinutes(10));
    assertThat(loginCode.maxAttempts()).isEqualTo(5);
    assertThat(loginCode.maxRequestsPerHour()).isEqualTo(5);
  }
}
