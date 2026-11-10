package org.mwolff.manban.entscheidungskette.domain;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Was die Rechnung der Kette liest, ohne Datenbank und ohne Uhr (Plan #2108 E1, E17). Der Dienst
 * füllt es aus den Fassaden von {@code card} und {@code comment}.
 *
 * @param karte die geöffnete Karte
 * @param vorfahren nächster Vorfahr zuerst
 * @param nachfahren stufenweise, je Stufe nach Nummer
 * @param ideen Ideen des Projekts, deren Zeile {@code Ergebnis:} eine Nummer der Kette nennen kann
 * @param aktivitaeten der Karten der Kette
 * @param kommentare der Karten der Kette
 */
public record KettenEingabe(
    Karte karte,
    List<Bezug> vorfahren,
    List<Bezug> nachfahren,
    List<Karte> ideen,
    List<Aktivitaet> aktivitaeten,
    List<Kommentar> kommentare) {

  /** Die Art einer Karte, wie sie die Lese-Fassade nennt. */
  public enum Art {
    FACHLICH,
    PLAN,
    IDEE,
    TASK,
    MENSCH,
    ARBEITSPAKET,
    VORHABEN
  }

  /** Was über eine verwandte Karte bekannt ist. */
  public enum Bezugsart {
    /** Im Projekt, archiviert oder nicht. */
    VORHANDEN,
    /** Im Papierkorb; bekannt ist nur die Nummer. */
    PAPIERKORB,
    /** Endgültig gelöscht; bekannt ist nur die bewahrte Nummer. */
    GELOESCHT,
    /** In einem anderen Projekt; bekannt ist nur diese Tatsache. */
    ANDERES_PROJEKT
  }

  /**
   * Eine Karte der Kette.
   *
   * @param wegBelegGeaendertAm letzte Änderung am Wegbeleg (Plan #2108 E14)
   */
  public record Karte(
      long id,
      int nummer,
      String titel,
      @Nullable String beschreibung,
      Art art,
      @Nullable String spalte,
      @Nullable String spaltenSchluessel,
      boolean archiviert,
      @Nullable Instant wegBelegGeaendertAm) {}

  /**
   * Eine verwandte Karte.
   *
   * @param nummer bei {@link Bezugsart#ANDERES_PROJEKT} {@code null}
   * @param karte nur bei {@link Bezugsart#VORHANDEN}
   */
  public record Bezug(Bezugsart art, @Nullable Integer nummer, @Nullable Karte karte) {}

  /** Ein Eintrag des Aktivitätsverlaufs. */
  public record Aktivitaet(
      long kartenId,
      @Nullable Long person,
      String typ,
      Instant zeitpunkt,
      @Nullable String herkunft,
      @Nullable String tokenName,
      @Nullable String agent,
      @Nullable String spaltenSchluessel) {}

  /**
   * Ein Kommentar mit Herkunft und Hand-Marke (Plan #2108 E3, E4).
   *
   * @param vonHandGeaendertAm letzte Änderung von Hand; {@code null} ohne
   * @param vonHandKopfzeile erste nichtleere Zeile der Fassung des Kits vor der Änderung von Hand
   */
  public record Kommentar(
      long id,
      long kartenId,
      String text,
      Instant zeitpunkt,
      @Nullable String herkunft,
      @Nullable String tokenName,
      @Nullable String agent,
      @Nullable Instant vonHandGeaendertAm,
      @Nullable String vonHandKopfzeile) {}
}
