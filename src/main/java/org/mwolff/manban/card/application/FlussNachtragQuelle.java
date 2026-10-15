package org.mwolff.manban.card.application;

import java.util.List;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Archivereignis;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Kartenstand;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Spaltenfolge;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Spaltenzeile;

/**
 * Ausgehender Port: die Rohdaten des einmaligen Nachtrags (Issue #1770, Plan #1765 E6), abgebildet
 * auf die Eingabe-Records von {@link org.mwolff.manban.card.domain.FlussRekonstruktion}.
 *
 * <p>Ein eigener Lesepfad, nicht {@link CardColumnTransitionRepository#findByBoardId}: Der joint
 * über das heutige {@code card.board_id}, der Nachtrag braucht das Board der Spalte ({@code
 * board_column.board_id}) — eine Karte, die das Board gewechselt hat, zählt bis dahin im alten.
 */
// PMD.ImplicitFunctionalInterface: bewusst KEIN @FunctionalInterface — der Port ist ein Lesepfad
// mit JDBC-Adapter, kein Lambda-Ziel.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface FlussNachtragQuelle {

  /** Was eine Karte zur Rekonstruktion beiträgt. */
  record Kartenverlauf(
      Kartenstand stand, List<Spaltenzeile> zeilen, List<Archivereignis> ereignisse) {}

  /**
   * Der ganze Bestand in einem Zug.
   *
   * @param karten jede Karte mit Zustand, auch im Archiv und im Papierkorb
   * @param folgen die heutigen Spaltenfolgen aller Boards
   */
  record Bestand(List<Kartenverlauf> karten, List<Spaltenfolge> folgen) {}

  /** Liest den Bestand. */
  Bestand lesen();
}
