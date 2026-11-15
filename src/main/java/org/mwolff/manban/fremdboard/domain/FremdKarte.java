package org.mwolff.manban.fremdboard.domain;

import java.time.Instant;
import java.util.List;

/**
 * Eine Karte, wie das fremde System sie liefert (Issue #2158, Plan #2152 A15). Den Status leitet
 * {@link StatusZuordnung} aus Labels und Zustand ab, die Herkunft {@link HerkunftAusText} aus der
 * Beschreibung.
 *
 * @param nummer Nummer der Karte im fremden System (bei GitLab die {@code iid})
 * @param titel Titel samt Präfix des Kits
 * @param beschreibung Text der Karte; leer, wenn sie keinen hat
 * @param labels Labelnamen an der Karte, als unveränderliche Kopie
 * @param geschlossen ob die Karte im fremden System geschlossen ist
 * @param angelegtAm Anlagezeitpunkt im fremden System
 * @param geaendertAm letzter Änderungszeitpunkt im fremden System
 */
public record FremdKarte(
    long nummer,
    String titel,
    String beschreibung,
    List<String> labels,
    boolean geschlossen,
    Instant angelegtAm,
    Instant geaendertAm) {

  /** Kopiert die Labels, damit die Karte nach dem Lesen unverändert bleibt. */
  public FremdKarte {
    labels = List.copyOf(labels);
  }
}
