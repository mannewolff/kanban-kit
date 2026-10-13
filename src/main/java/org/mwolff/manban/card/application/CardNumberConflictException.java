package org.mwolff.manban.card.application;

import java.io.Serializable;
import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
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
 *
 * <p>Je Fall eine Fabrikmethode mit eigenem Meldungsschlüssel (Issue #1642).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public final class CardNumberConflictException extends RuntimeException
    implements UebersetzbareMeldung {

  private final String meldungsschluessel;
  private final Serializable[] meldungsargumente;

  private CardNumberConflictException(String schluessel, String text, Serializable... argumente) {
    super(text);
    this.meldungsschluessel = schluessel;
    this.meldungsargumente = argumente;
  }

  /** Der Idempotenz-Schlüssel gehört schon zu einer Karte mit anderer Nummer. */
  public static CardNumberConflictException schluesselGehoertAndererKarte(
      Integer vorhanden, Integer angefordert) {
    return new CardNumberConflictException(
        "card.nummer.schluessel-gehoert-anderer-karte",
        "Der Schluessel gehoert bereits zu Karte #"
            + vorhanden
            + ", angefordert war #"
            + angefordert
            + ".",
        vorhanden,
        angefordert);
  }

  /** Das Projekt enthält Karten außerhalb eines Imports. */
  public static CardNumberConflictException importfremdeKarten() {
    return new CardNumberConflictException(
        "card.nummer.importfremde-karten",
        "Das Projekt enthaelt Karten ausserhalb eines Imports — eine vorgegebene Nummer wird nur"
            + " in ein Projekt ohne importfremde Karten uebernommen.");
  }

  /** Die Nummer ist im Projekt schon vergeben. */
  public static CardNumberConflictException nummerVergeben(Integer nummer) {
    return new CardNumberConflictException(
        "card.nummer.vergeben", "Nummer #" + nummer + " ist bereits vergeben.", nummer);
  }

  @Override
  public String schluessel() {
    return meldungsschluessel;
  }

  @Override
  public Object[] argumente() {
    return meldungsargumente.clone();
  }
}
