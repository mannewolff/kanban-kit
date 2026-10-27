package org.mwolff.manban.auth.domain;

/**
 * Gewähltes Erscheinungsbild eines Kontos (Issue #1911): {@link #SYSTEM} folgt dem Farbschema von
 * Browser bzw. Betriebssystem, {@link #LIGHT} und {@link #DARK} legen es fest.
 */
public enum ColorScheme {
  SYSTEM,
  LIGHT,
  DARK
}
