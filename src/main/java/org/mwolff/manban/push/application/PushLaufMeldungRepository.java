package org.mwolff.manban.push.application;

import java.time.Instant;

/**
 * Persistenz-Port der gemeldeten Läufe (Issue #1997, Plan #1989 E12): Je Lauf hält eine Zeile fest,
 * dass er gemeldet wurde. Genutzt vom {@link LaufMeldeService} (Issue #1998).
 */
// PMD.ImplicitFunctionalInterface: bewusst KEIN @FunctionalInterface — ein Persistenz-Port, kein
// Lambda-Ziel.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface PushLaufMeldungRepository {

  /**
   * Merkt den Lauf als gemeldet, sofern er es noch nicht ist.
   *
   * @param grund der Meldegrund, wie der Meldejob ihn benennt
   * @return wahr, wenn dieser Aufruf die Meldung festgehalten hat; falsch, wenn der Lauf schon
   *     gemeldet war — so bleibt es auch bei gleichzeitigen Aufrufen bei höchstens einer Nachricht
   */
  boolean merke(long nightRunId, Instant gemeldetAt, String grund);
}
