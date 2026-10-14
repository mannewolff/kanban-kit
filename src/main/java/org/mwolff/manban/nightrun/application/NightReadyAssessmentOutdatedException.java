package org.mwolff.manban.nightrun.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die gemeldete Bewertung der Ready-Karten ist älter als die gespeicherte (Issue #1726).
 *
 * <p>409 und nicht 400: Die Meldung ist in sich gültig, nur hat das Board schon einen jüngeren
 * Stand — etwa weil zwei Läufe kurz nacheinander bewertet haben und die Meldungen sich überholt
 * haben. Die ältere würde die jüngere sonst verdrängen.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class NightReadyAssessmentOutdatedException extends RuntimeException {

  private static final long serialVersionUID = 1L;
}
