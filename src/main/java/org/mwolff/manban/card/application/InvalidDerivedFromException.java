package org.mwolff.manban.card.application;

import java.io.Serializable;
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
 *
 * <p>Je Fall eine Fabrikmethode mit eigenem Meldungsschlüssel (Issue #1642).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public final class InvalidDerivedFromException extends InvalidDependencyException
    implements FieldScopedException {

  /** Feldname im {@code fieldErrors}-Extension des Problemdokuments. */
  public static final String FIELD_NAME = "derivedFrom";

  private InvalidDerivedFromException(String schluessel, String text, Serializable... argumente) {
    super(schluessel, text, argumente);
  }

  /** Ein Vorhaben trägt keine Herkunft. */
  public static InvalidDerivedFromException anVorhaben() {
    return new InvalidDerivedFromException(
        "card.herkunft.an-vorhaben", "Ein Vorhaben kann keine Herkunft tragen");
  }

  /** Die Kartennummer der Herkunft gibt es im Projekt nicht. */
  public static InvalidDerivedFromException unbekannteNummer(Integer nummer) {
    return new InvalidDerivedFromException(
        "card.herkunft.unbekannte-nummer",
        "Unbekannte Kartennummer als Herkunft: " + nummer,
        nummer);
  }

  /** Eine Karte stammt von sich selbst ab. */
  public static InvalidDerivedFromException vonSichSelbst() {
    return new InvalidDerivedFromException(
        "card.herkunft.von-sich-selbst", "Eine Karte kann nicht von sich selbst abstammen");
  }

  /** Die Herkunftskette ist zu lang. */
  public static InvalidDerivedFromException ketteZuLang(Integer glieder) {
    return new InvalidDerivedFromException(
        "card.herkunft.kette-zu-lang",
        "Herkunftskette erreicht " + glieder + " Glieder — vermutlich zyklisch",
        glieder);
  }

  /** Die Herkunft schlösse einen Zyklus. */
  public static InvalidDerivedFromException zyklus() {
    return new InvalidDerivedFromException(
        "card.herkunft.zyklus", "Herkunft wuerde einen Zyklus bilden");
  }

  @Override
  public String field() {
    return FIELD_NAME;
  }
}
