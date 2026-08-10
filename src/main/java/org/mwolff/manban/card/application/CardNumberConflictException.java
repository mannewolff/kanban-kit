package org.mwolff.manban.card.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Eine vorgegebene Kartennummer lässt sich im Zielprojekt nicht vergeben (Issue #565).
 *
 * <p>Zustandskonflikt, kein Requestfehler — deshalb 409 und nicht 400: Derselbe Aufruf wäre gegen
 * ein anderes Projekt oder zu einem früheren Zeitpunkt gültig gewesen. Die Linie folgt der
 * Entscheidung aus #496 (Datenintegritätskonflikte als 409 statt 500).
 *
 * <p>Drei Anlässe: die Nummer ist bereits vergeben (auch von einer archivierten oder in den
 * Papierkorb verschobenen Karte), das Zielprojekt enthält importfremde Karten (Vorbedingung), oder
 * der Idempotenz-Schlüssel trifft eine bestehende Karte mit abweichender Nummer.
 *
 * <p>Der letzte Fall ist der heikelste: Ohne ihn bekäme der Aufrufer stillschweigend eine andere
 * Identität als angefordert zurück — und merkte es erst, wenn migrierte Abhängigkeiten ins Leere
 * zeigen.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class CardNumberConflictException extends RuntimeException {

  public CardNumberConflictException(String message) {
    super(message);
  }
}
