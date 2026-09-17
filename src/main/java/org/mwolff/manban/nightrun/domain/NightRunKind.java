package org.mwolff.manban.nightrun.domain;

/**
 * Gattung eines Eintrags in der Nachtlauf-Auswertung (Issue #1010, Plan #1007).
 *
 * <p>Seit {@code V34} teilen sich zwei Gattungen dieselben Tabellen: der Nachtlauf, der sie bisher
 * allein trug, und die interaktive Sitzung. Die Gattung sagt, <b>was</b> der Eintrag ist.
 *
 * <p>Nicht zu verwechseln mit den beiden Nachbarn: {@link NightRunOrigin} ist die <b>Herkunft</b> —
 * auf welchem Weg der Eintrag ans Board kam ({@code UPLOAD} oder {@code TOKEN}) —, {@link
 * NightRunMode} die <b>Art</b> des Laufs. Eine interaktive Sitzung trägt Gattung {@link
 * #INTERACTIVE}, Herkunft {@link NightRunOrigin#TOKEN} und Art {@link NightRunMode#INTERACTIVE};
 * eine Sitzung als {@link NightRunMode#IMPLEMENTATION} auszugeben fälschte die bestehende
 * Nachtlauf-Auswertung, die über {@code mode} unterscheidet.
 */
public enum NightRunKind {

  /** Ein Nachtlauf des Runners — die Gattung, die es vor {@code V34} allein gab. */
  NIGHT,

  /** Eine interaktive Sitzung am Rechner eines Menschen. */
  INTERACTIVE
}
