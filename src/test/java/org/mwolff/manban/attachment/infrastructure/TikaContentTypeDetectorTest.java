package org.mwolff.manban.attachment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit-Test der Tika-basierten Content-Type-Erkennung aus den Magic-Bytes. */
class TikaContentTypeDetectorTest {

    private static final byte[] PNG_MAGIC = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    @Test
    void detect_recognizesPngFromMagicBytes() {
        // Given
        TikaContentTypeDetector detector = new TikaContentTypeDetector();

        // When
        String type = detector.detect(PNG_MAGIC, "irgendwas.bin");

        // Then: der gemeldete Dateiname wird ignoriert, der Typ kommt aus den Magic-Bytes
        assertThat(type).isEqualTo("image/png");
    }
}
