package org.mwolff.manban.card.application;

import org.mwolff.manban.common.FieldScopedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Herkunft einer Karte verweist auf eine unbekannte Nummer, auf die Karte selbst oder schließt
 * einen Zyklus.
 *
 * <p>Erbt bewusst von {@link InvalidDependencyException}: Fachlich ist es derselbe Fehler — ein
 * Verweis auf eine Karte, die es so nicht geben darf —, und Aufrufer, die auf die Oberklasse
 * prüfen, bleiben gültig.
 *
 * <p>Eigene Klasse trotzdem, aus einem Grund am Draht: Derselbe Status 400 mit fast wortgleichem
 * Text entsteht im selben Aufruf auch durch Abhängigkeits-Fehler („Unbekannte Kartennummer: N"
 * gegen „Unbekannte Kartennummer als Herkunft: N"). Nur über den Typ kann {@code
 * GlobalExceptionHandler} die Meldung dem Feld {@code derivedFrom} zuordnen; ohne diese Zuordnung
 * zeigt die Kartenmaske (Issue #608) Abhängigkeits-Fehler am Herkunftsfeld an.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidDerivedFromException extends InvalidDependencyException
    implements FieldScopedException {

  /** Feldname im {@code fieldErrors}-Extension des Problemdokuments. */
  public static final String FIELD_NAME = "derivedFrom";

  public InvalidDerivedFromException(String message) {
    super(message);
  }

  @Override
  public String field() {
    return FIELD_NAME;
  }
}
