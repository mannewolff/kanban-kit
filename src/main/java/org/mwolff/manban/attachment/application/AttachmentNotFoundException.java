package org.mwolff.manban.attachment.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Anhang existiert nicht. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AttachmentNotFoundException extends RuntimeException {

    public AttachmentNotFoundException() {
        super("Anhang nicht gefunden");
    }
}
