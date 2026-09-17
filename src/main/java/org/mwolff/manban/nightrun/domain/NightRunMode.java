package org.mwolff.manban.nightrun.domain;

/**
 * Betriebsart, in der ein Nachtlauf gestartet wurde.
 *
 * <p>Übernommen aus dem Parser {@code frontend/src/lib/nightRunLog.ts} (Plan #718, A13), nicht neu
 * erfunden; {@code NightRunErrorClassSyncTest} hält die Werte an der Migration gegen.
 */
public enum NightRunMode {

  /** Implementierungs-Lauf: Arbeitspakete aus Ready werden umgesetzt. */
  IMPLEMENTATION,

  /** Prüf-Lauf: Kandidaten aus dem Backlog werden begutachtet. */
  REVIEW,

  /**
   * Ketten-Lauf ({@code night.mjs --kette}): eine Kette je fachlichem Issue, die Plan, Review,
   * Arbeitspakete und Abdeckung nacheinander durchläuft (Issue #842).
   */
  CHAIN,

  /**
   * Interaktive Sitzung: ein Mensch arbeitet am Rechner, kein Runner (Issue #1010, Plan #1007,
   * E23).
   *
   * <p>Die Art gehört zur Gattung {@link NightRunKind#INTERACTIVE} und steht bewusst neben {@link
   * #IMPLEMENTATION}: Eine Sitzung als Implementierungs-Lauf auszugeben fälschte die bestehende
   * Nachtlauf-Auswertung, die über {@code mode} unterscheidet.
   */
  INTERACTIVE
}
