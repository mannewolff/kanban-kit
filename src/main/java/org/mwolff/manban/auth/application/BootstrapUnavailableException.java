package org.mwolff.manban.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Bootstrap nicht mehr möglich, weil bereits ein Plattform-Admin existiert. */
@ResponseStatus(HttpStatus.CONFLICT)
public class BootstrapUnavailableException extends RuntimeException {

    public BootstrapUnavailableException() {
        super("Es existiert bereits ein Plattform-Admin");
    }
}
