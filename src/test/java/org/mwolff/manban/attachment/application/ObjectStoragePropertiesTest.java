package org.mwolff.manban.attachment.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests der Defaulting-Logik im Kompaktkonstruktor von {@link ObjectStorageProperties}. */
class ObjectStoragePropertiesTest {

    @Test
    void appliesDefaults_whenAllValuesNull() {
        // When
        ObjectStorageProperties props = new ObjectStorageProperties(null, null, null, null, null);

        // Then
        assertThat(props.endpoint()).isEqualTo("http://localhost:9000");
        assertThat(props.accessKey()).isEqualTo("manban");
        assertThat(props.secretKey()).isEqualTo("manban-minio");
        assertThat(props.bucket()).isEqualTo("manban");
        assertThat(props.maxPerCard()).isEqualTo(20);
    }

    @Test
    void appliesDefaults_whenStringsBlankAndMaxNonPositive() {
        // When
        ObjectStorageProperties props = new ObjectStorageProperties("  ", "  ", "  ", "  ", 0);

        // Then
        assertThat(props.endpoint()).isEqualTo("http://localhost:9000");
        assertThat(props.accessKey()).isEqualTo("manban");
        assertThat(props.secretKey()).isEqualTo("manban-minio");
        assertThat(props.bucket()).isEqualTo("manban");
        assertThat(props.maxPerCard()).isEqualTo(20);
    }

    @Test
    void keepsProvidedValues() {
        // When
        ObjectStorageProperties props =
                new ObjectStorageProperties("https://s3.example", "key", "secret", "files", 5);

        // Then
        assertThat(props.endpoint()).isEqualTo("https://s3.example");
        assertThat(props.accessKey()).isEqualTo("key");
        assertThat(props.secretKey()).isEqualTo("secret");
        assertThat(props.bucket()).isEqualTo("files");
        assertThat(props.maxPerCard()).isEqualTo(5);
    }
}
