package org.mwolff.manban.card.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Karte hat ihre Spalte verlassen, während der laufende Umzug seine Sperren erwarb (Issue
 * #499).
 *
 * <p>Positionsändernde Operationen sperren die Spalten, die sie anfassen — Quelle und Ziel — in
 * <em>einem</em> nach ID sortierten Aufruf; nur so kann kein Deadlock entstehen. Welche Spalte die
 * Quelle ist, steht aber erst nach dem Sperren fest. Hat eine parallele Transaktion die Karte
 * zwischenzeitlich in eine dritte Spalte verschoben, ist genau diese Spalte nicht gesperrt: Ein
 * Weiterarbeiten würde sie ungeschützt reindizieren und dort eine Lücke hinterlassen.
 *
 * <p>Statt die Sperrmenge nachträglich zu erweitern (das bräche die Sortierordnung und damit die
 * Deadlock-Freiheit), endet der zweite Aufruf hier mit 409. Fachlich ist das der ehrliche Befund:
 * Zwei Benutzer haben dieselbe Karte gleichzeitig an verschiedene Orte verschoben — der zweite
 * arbeitet auf einem Board, das er so nicht mehr vor sich hat.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class CardMovedConcurrentlyException extends RuntimeException {

  public CardMovedConcurrentlyException() {
    super("Die Karte wurde zwischenzeitlich in eine andere Spalte verschoben");
  }
}
