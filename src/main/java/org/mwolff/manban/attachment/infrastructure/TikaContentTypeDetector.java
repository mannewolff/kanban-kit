package org.mwolff.manban.attachment.infrastructure;

import org.apache.tika.Tika;
import org.mwolff.manban.attachment.application.ContentTypeDetector;
import org.springframework.stereotype.Component;

/**
 * Content-Type-Erkennung mit Apache Tika aus den Magic-Bytes des Inhalts (und dem
 * Dateinamen als Hinweis). Bewusst NICHT dem vom Client gemeldeten Typ vertrauen.
 */
@Component
class TikaContentTypeDetector implements ContentTypeDetector {

    private final Tika tika = new Tika();

    @Override
    public String detect(byte[] content, String filename) {
        return tika.detect(content, filename);
    }
}
