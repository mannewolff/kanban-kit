package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Tests der Defaulting-Logik im Kompaktkonstruktor von {@link AuthProperties}. */
class AuthPropertiesTest {

    @Test
    void appliesDefaults_whenAllValuesNull() {
        // When
        AuthProperties props = new AuthProperties(null, null, null, null, null, null);

        // Then
        assertThat(props.baseUrl()).isEqualTo("http://localhost:8080");
        assertThat(props.verificationTtl()).isEqualTo(Duration.ofHours(24));
        assertThat(props.resetTtl()).isEqualTo(Duration.ofHours(1));
        assertThat(props.sessionSecret()).isEqualTo("dev-only-insecure-secret-change-me");
        assertThat(props.sessionTtl()).isEqualTo(Duration.ofDays(7));
        assertThat(props.cookieSecure()).isTrue();
    }

    @Test
    void appliesDefaults_whenStringsAreBlank() {
        // When
        AuthProperties props = new AuthProperties("   ", null, "   ", null, null, null);

        // Then
        assertThat(props.baseUrl()).isEqualTo("http://localhost:8080");
        assertThat(props.sessionSecret()).isEqualTo("dev-only-insecure-secret-change-me");
    }

    @Test
    void keepsProvidedValues() {
        // When
        AuthProperties props = new AuthProperties(
                "https://manban.example", Duration.ofHours(2), "s3cr3t",
                Duration.ofDays(1), Boolean.FALSE, Duration.ofMinutes(30));

        // Then
        assertThat(props.baseUrl()).isEqualTo("https://manban.example");
        assertThat(props.verificationTtl()).isEqualTo(Duration.ofHours(2));
        assertThat(props.sessionSecret()).isEqualTo("s3cr3t");
        assertThat(props.sessionTtl()).isEqualTo(Duration.ofDays(1));
        assertThat(props.cookieSecure()).isFalse();
        assertThat(props.resetTtl()).isEqualTo(Duration.ofMinutes(30));
    }
}
