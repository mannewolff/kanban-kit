package org.mwolff.manban.fremdboard.domain;

import java.util.List;

/**
 * Die Nummern aller Karten des angebundenen Projekts, offene wie geschlossene (Issue #2168),
 * getrennt nach ihrer Vertraulichkeit (Issue #2200). Der Zugang des Spiegels darf im fremden System
 * mehr lesen als ein Mitglied des Projekts; eine vertrauliche Karte wird darum nicht gespiegelt,
 * und eine schon gespiegelte entfernt der Vollabgleich samt Kommentaren — auch aus dem Papierkorb.
 *
 * @param sichtbar Nummern der Karten, die gespiegelt werden dürfen, als unveränderliche Kopie
 * @param vertraulich Nummern der vertraulichen Karten, als unveränderliche Kopie; nur die Nummer
 *     verlässt die Quelle, kein Inhalt
 */
public record FremdNummern(List<Long> sichtbar, List<Long> vertraulich) {

  /** Kopiert beide Listen, damit die Nummern nach dem Lesen unverändert bleiben. */
  public FremdNummern {
    sichtbar = List.copyOf(sichtbar);
    vertraulich = List.copyOf(vertraulich);
  }
}
