package org.mwolff.manban.nightrun.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Karte ist im Projekt kein Vorhaben (Issue #2005, Plan #1990 Paket 5) — die Kostenzeile eines
 * Vorhabens gibt es für sie nicht.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class VorhabenNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;
}
