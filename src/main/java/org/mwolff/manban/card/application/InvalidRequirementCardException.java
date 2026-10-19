package org.mwolff.manban.card.application;

import java.io.Serializable;
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
 *
 * <p>Je Fall eine Fabrikmethode mit eigenem Meldungsschlüssel (Issue #1642).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public final class InvalidRequirementCardException extends InvalidDependencyException
    implements FieldScopedException {

  /** Feldname im {@code fieldErrors}-Extension des Problemdokuments. */
  public static final String FIELD_NAME = "requirementCardNumber";

  private InvalidRequirementCardException(
      String schluessel, String text, Serializable... argumente) {
    super(schluessel, text, argumente);
  }

  /** Die Karte ist kein Vorhaben. */
  public static InvalidRequirementCardException keinVorhaben(Integer nummer) {
    return new InvalidRequirementCardException(
        "card.anforderung.kein-vorhaben",
        "Eine Anforderung laesst sich nur an einem Vorhaben setzen: " + nummer,
        nummer);
  }

  /** Die Kartennummer der Anforderung gibt es im Projekt nicht. */
  public static InvalidRequirementCardException unbekannteAnforderungsnummer(Integer nummer) {
    return new InvalidRequirementCardException(
        "card.anforderung.unbekannte-nummer",
        "Unbekannte Kartennummer als Anforderung: " + nummer,
        nummer);
  }

  /** Das Vorhaben verweist auf sich selbst. */
  public static InvalidRequirementCardException eigene() {
    return new InvalidRequirementCardException(
        "card.anforderung.eigene", "Ein Vorhaben kann nicht seine eigene Anforderung sein");
  }

  /** Die Anforderung liegt auf einem anderen Board. */
  public static InvalidRequirementCardException fremdesBoard(Integer nummer) {
    return new InvalidRequirementCardException(
        "card.anforderung.fremdes-board",
        "Die Anforderung liegt nicht auf dem Board des Vorhabens: " + nummer,
        nummer);
  }

  @Override
  public String field() {
    return FIELD_NAME;
  }
}
