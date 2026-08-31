package org.mwolff.manban.card.application;

import org.mwolff.manban.common.FieldScopedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Anforderungskarte eines Vorhabens verweist auf eine unbekannte Nummer, auf eine Karte eines
 * fremden Boards oder auf das Vorhaben selbst — oder das Ziel ist gar kein Vorhaben.
 *
 * <p>Erbt wie {@link InvalidDerivedFromException} von {@link InvalidDependencyException}: Fachlich
 * ist es derselbe Fehler — ein Verweis auf eine Karte, die es so nicht geben darf —, und Aufrufer,
 * die auf die Oberklasse prüfen, bleiben gültig.
 *
 * <p>Eigene Klasse trotzdem, aus demselben Grund am Draht wie dort: Im selben Aufruf entstehen
 * Status 400 mit fast wortgleichem Text auch aus Abhängigkeits- und Herkunftsfehlern. Nur über den
 * Typ kann {@code GlobalExceptionHandler} die Meldung dem Feld {@link #FIELD_NAME} zuordnen; ohne
 * diese Zuordnung zeigte die Kachel den Fehler an einem fremden Eingabefeld an.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidRequirementCardException extends InvalidDependencyException
    implements FieldScopedException {

  /** Feldname im {@code fieldErrors}-Extension des Problemdokuments. */
  public static final String FIELD_NAME = "requirementCardNumber";

  public InvalidRequirementCardException(String message) {
    super(message);
  }

  @Override
  public String field() {
    return FIELD_NAME;
  }
}
